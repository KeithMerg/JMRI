package jmri.jmrit.beantable.block;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.*;

import jmri.BeanSetting;
import jmri.Block;
import jmri.BlockManager;
import jmri.InstanceManager;
import jmri.NamedBean;
import jmri.Path;
import jmri.Section;
import jmri.SectionManager;
import jmri.Transit;
import jmri.TransitManager;
import jmri.TransitSection;
import jmri.Turnout;
import jmri.jmrit.display.layoutEditor.LayoutBlockManager;
import jmri.swing.NamedBeanComboBox;
import jmri.util.JmriJFrame;

/**
 * Shows what is known about one Block in one place: its state, Sensor, value
 * and settings, each of its Paths with the Turnouts they set and the signal
 * facing into the next Block, the Sections and Transits it is in, and where it
 * is used. Copy puts the text on the clipboard.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class BlockDetailsFrame extends JmriJFrame {

    private final NamedBeanComboBox<Block> blockBox =
            new NamedBeanComboBox<>(InstanceManager.getDefault(BlockManager.class));
    private final JTextArea text = new JTextArea(30, 70);

    public BlockDetailsFrame() {
        super(Bundle.getMessage("BlockDetailsTitle"));
    }

    @Override
    public void initComponents() {
        addHelpMenu("package.jmri.jmrit.beantable.BlockTable#blockDetails", true);
        getContentPane().setLayout(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel(Bundle.getMessage("BlockDetailsBlock")));
        blockBox.setAllowNull(true);
        blockBox.addActionListener(e -> show(blockBox.getSelectedItem()));
        top.add(blockBox);
        JButton refresh = new JButton(Bundle.getMessage("BlockDetailsRefresh"));
        refresh.addActionListener(e -> show(blockBox.getSelectedItem()));
        top.add(refresh);
        getContentPane().add(top, BorderLayout.NORTH);
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
        getContentPane().add(new JScrollPane(text), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copy = new JButton(Bundle.getMessage("BlockValuesCopy"));
        copy.setToolTipText(Bundle.getMessage("BlockDetailsCopyHint"));
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.getText()), null));
        buttons.add(copy);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
    }

    /**
     * Show a Block's details.
     *
     * @param b the Block; null for none
     */
    public void show(@CheckForNull Block b) {
        if (b != blockBox.getSelectedItem()) {
            blockBox.setSelectedItem(b);
        }
        text.setText(b == null ? "" : details(b));
        text.setCaretPosition(0);
    }

    /**
     * @return the text shown
     */
    public String getText() {
        return text.getText();
    }

    /**
     * Describe a Block, as shown in the window.
     *
     * @param b the Block
     * @return the text
     */
    @Nonnull
    public static String details(@Nonnull Block b) {
        StringBuilder sb = new StringBuilder();
        line(sb, "", "BlockDetailsName", b.getDisplayName(NamedBean.DisplayOptions.USERNAME_SYSTEMNAME));
        line(sb, "", "BlockDetailsState", b.describeState(b.getState()));
        line(sb, "", "BlockDetailsSensor", b.getSensor() == null ? Bundle.getMessage("BlockValuesNone")
                : b.getSensor().getDisplayName() + " (" + b.getSensor().describeState(b.getSensor().getKnownState()) + ")");
        line(sb, "", "BlockDetailsValue", b.getValue() == null ? Bundle.getMessage("BlockValuesNone")
                : b.getValue().toString());
        line(sb, "", "BlockDetailsLength", String.format("%.0f mm", b.getLengthMm()));
        if (b.getIsGhost()) {
            line(sb, "", "BlockDetailsGhost", Bundle.getMessage("BlockDetailsYes"));
        }
        if (b.getPermissiveWorking()) {
            line(sb, "", "BlockDetailsPermissive", Bundle.getMessage("BlockDetailsYes"));
        }
        if (b.getBlockSpeed() != null && !b.getBlockSpeed().isEmpty()) {
            line(sb, "", "BlockDetailsSpeed", b.getBlockSpeed());
        }

        sb.append(Bundle.getMessage("BlockDetailsPaths", b.getPaths().size())).append('\n');
        LayoutBlockManager lbm = InstanceManager.getNullableDefault(LayoutBlockManager.class);
        for (Path p : b.getPaths()) {
            Block to = p.getBlock();
            sb.append("  ").append(Bundle.getMessage("BlockDetailsPathTo",
                    to == null ? "?" : to.getDisplayName())).append('\n');
            for (BeanSetting s : p.getSettings()) {
                NamedBean bean = s.getBean();
                String state = bean instanceof Turnout ? bean.describeState(s.getSetting()) : String.valueOf(s.getSetting());
                sb.append("    ").append(Bundle.getMessage("BlockDetailsSetting",
                        bean == null ? s.getBeanName() : bean.getDisplayName(), state)).append('\n');
            }
            if (to != null && lbm != null && lbm.getLayoutBlock(b) != null && lbm.getLayoutBlock(to) != null) {
                NamedBean mast = lbm.getFacingSignalMast(b, to);
                if (mast != null) {
                    sb.append("    ").append(Bundle.getMessage("BlockDetailsFacingMast", mast.getDisplayName()))
                            .append('\n');
                }
                NamedBean head = lbm.getFacingSignalHead(b, to);
                if (head != null) {
                    sb.append("    ").append(Bundle.getMessage("BlockDetailsFacingHead", head.getDisplayName()))
                            .append('\n');
                }
            }
        }

        StringBuilder sections = new StringBuilder();
        StringBuilder transits = new StringBuilder();
        for (Section s : InstanceManager.getDefault(SectionManager.class).getNamedBeanSet()) {
            if (s.getBlockList().contains(b)) {
                sections.append("  ").append(s.getDisplayName()).append('\n');
                for (Transit t : InstanceManager.getDefault(TransitManager.class).getNamedBeanSet()) {
                    for (TransitSection ts : t.getTransitSectionList()) {
                        if (ts.getSection() == s) {
                            transits.append("  ").append(Bundle.getMessage("BlockDetailsTransitSection",
                                    t.getDisplayName(), ts.getSequenceNumber(), s.getDisplayName())).append('\n');
                        }
                    }
                }
            }
        }
        sb.append(Bundle.getMessage("BlockDetailsSections")).append('\n')
                .append(sections.length() == 0 ? "  " + Bundle.getMessage("BlockValuesNone") + "\n" : sections);
        sb.append(Bundle.getMessage("BlockDetailsTransits")).append('\n')
                .append(transits.length() == 0 ? "  " + Bundle.getMessage("BlockValuesNone") + "\n" : transits);
        sb.append('\n').append(jmri.jmrit.whereused.BlockWhereUsed.getWhereUsed(b).getText());
        return sb.toString();
    }

    private static void line(StringBuilder sb, String indent, String key, String value) {
        sb.append(indent).append(Bundle.getMessage(key, value)).append('\n');
    }
}
