# Clear out (set to blank) all the Block contents.
# Run at startup after any panel XML files have been loaded, 
# this will make sure that no blocks currently contain 
# any information, e.g. train numbers or names.
# It overrides anything that might be saved in the blockvalues.xml 
# file from one run to the next.
#
# Bob Jacobsen   (C) 2026
#
# Since JMRI 5.19.2 the Block Table's Values > Clear Values does this without a script.

for block in blocks.getNamedBeanSet() :
    block.setValue("")

