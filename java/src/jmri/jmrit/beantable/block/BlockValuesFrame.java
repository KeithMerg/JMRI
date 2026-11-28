package jmri.jmrit.beantable.block;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

import jmri.Block;
import jmri.BlockManager;
import jmri.InstanceManager;
import jmri.jmrit.roster.Roster;
import jmri.jmrit.roster.RosterEntry;
import jmri.util.JmriJFrame;
import jmri.util.ThreadingUtil;

/**
 * Lists the occupied Blocks with no value, so the train in each can be named,
 * as at the start of a session: chosen from the Roster or typed. A Roster entry
 * chosen is put in the Block as itself, so a Layout Editor Block contents icon
 * shows it as a Roster entry. Blocks are added and removed as they become
 * occupied or empty.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class BlockValuesFrame extends JmriJFrame {

    private final ValuesModel model = new ValuesModel();
    private final JTable table = new JTable(model);
    private final JCheckBox withValues = new JCheckBox(Bundle.getMessage("BlockValuesIncludeWithValue"));
    private final JLabel summary = new JLabel();
    private final transient PropertyChangeListener blockListener =
            e -> {
                if ("state".equals(e.getPropertyName())) {
                    ThreadingUtil.runOnGUIEventually(this::refresh);
                }
            };
    private final transient List<Block> listened = new ArrayList<>();

    public BlockValuesFrame() {
        super(Bundle.getMessage("BlockValuesTitle"));
    }

    @Override
    public void initComponents() {
        addHelpMenu("package.jmri.jmrit.beantable.BlockTable#nameTrains", true);
        getContentPane().setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel(Bundle.getMessage("BlockValuesHint")));
        withValues.setToolTipText(Bundle.getMessage("BlockValuesIncludeWithValueHint"));
        withValues.addActionListener(e -> refresh());
        top.add(withValues);
        getContentPane().add(top, BorderLayout.NORTH);

        JComboBox<String> chooser = new JComboBox<>();
        chooser.setEditable(true);
        chooser.addItem("");
        for (RosterEntry re : Roster.getDefault().getAllEntries()) {
            chooser.addItem(re.getId());
        }
        table.getColumnModel().getColumn(ValuesModel.NEW).setCellEditor(new DefaultCellEditor(chooser));
        table.setAutoCreateRowSorter(true);
        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(summary, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton apply = new JButton(Bundle.getMessage("BlockValuesApply"));
        apply.setToolTipText(Bundle.getMessage("BlockValuesApplyHint"));
        apply.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            apply();
        });
        buttons.add(apply);
        JButton copy = new JButton(Bundle.getMessage("BlockValuesCopy"));
        copy.setToolTipText(Bundle.getMessage("BlockValuesCopyHint"));
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text()), null));
        buttons.add(copy);
        bottom.add(buttons, BorderLayout.EAST);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        for (Block b : InstanceManager.getDefault(BlockManager.class).getNamedBeanSet()) {
            b.addPropertyChangeListener(blockListener);
            listened.add(b);
        }
        refresh();
        pack();
    }

    /**
     * List the occupied Blocks again, keeping what has been typed or chosen
     * for those still listed.
     */
    public void refresh() {
        Map<Block, String> typed = new LinkedHashMap<>();
        for (Row r : model.rows) {
            if (!r.newValue.isEmpty()) {
                typed.put(r.block, r.newValue);
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Block b : InstanceManager.getDefault(BlockManager.class).getNamedBeanSet()) {
            if (b.getState() == Block.OCCUPIED && (withValues.isSelected() || isEmpty(b.getValue()))) {
                Row r = new Row(b);
                r.newValue = typed.getOrDefault(b, "");
                rows.add(r);
            }
        }
        model.setRows(rows);
        summary.setText(Bundle.getMessage("BlockValuesSummary", rows.size()));
    }

    private static boolean isEmpty(@CheckForNull Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    /**
     * Put each name chosen or typed in its Block: a Roster entry as itself,
     * anything else as text.
     *
     * @return the number of Blocks given a value
     */
    public int apply() {
        int n = 0;
        for (Row r : model.rows) {
            String v = r.newValue.trim();
            if (!v.isEmpty()) {
                RosterEntry re = Roster.getDefault().getEntryForId(v);
                r.block.setValue(re != null ? re : v);
                n++;
            }
        }
        refresh();
        return n;
    }

    /**
     * Choose or type the name for a listed Block, as in its New Value cell.
     *
     * @param block the Block
     * @param value the name; empty for none
     */
    public void setNewValue(Block block, String value) {
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).block == block) {
                model.setValueAt(value, i, ValuesModel.NEW);
            }
        }
    }

    /**
     * @return the Blocks listed
     */
    public List<Block> getListed() {
        List<Block> l = new ArrayList<>();
        model.rows.forEach(r -> l.add(r.block));
        return l;
    }

    /**
     * The Blocks listed as text, one a line: the Block, then its value.
     *
     * @return the text
     */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (int v = 0; v < table.getRowCount(); v++) {
            Row r = model.rows.get(table.convertRowIndexToModel(v));
            sb.append(r.block.getDisplayName()).append(": ")
                    .append(isEmpty(r.block.getValue()) ? Bundle.getMessage("BlockValuesNone")
                            : r.block.getValue().toString())
                    .append('\n');
        }
        return sb.toString();
    }

    @Override
    public void dispose() {
        listened.forEach(b -> b.removePropertyChangeListener(blockListener));
        listened.clear();
        super.dispose();
    }

    private static final class Row {

        final Block block;
        String newValue = "";

        Row(Block b) {
            block = b;
        }
    }

    static final class ValuesModel extends AbstractTableModel {

        static final int BLOCK = 0;
        static final int SENSOR = 1;
        static final int VALUE = 2;
        static final int NEW = 3;
        private static final String[] KEYS = {"BlockValuesColBlock", "BlockValuesColSensor",
            "BlockValuesColValue", "BlockValuesColNew"};

        private transient List<Row> rows = new ArrayList<>();

        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return KEYS.length;
        }

        @Override
        public String getColumnName(int col) {
            return Bundle.getMessage(KEYS[col]);
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == NEW;
        }

        @Override
        public Object getValueAt(int row, int col) {
            Row r = rows.get(row);
            switch (col) {
                case BLOCK:
                    return r.block.getDisplayName();
                case SENSOR:
                    return r.block.getSensor() == null ? "" : r.block.getSensor().getDisplayName();
                case VALUE:
                    return isEmpty(r.block.getValue()) ? "" : r.block.getValue().toString();
                case NEW:
                    return r.newValue;
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == NEW) {
                rows.get(row).newValue = value == null ? "" : value.toString();
                fireTableCellUpdated(row, col);
            }
        }
    }
}
