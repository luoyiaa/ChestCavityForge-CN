package net.tigereye.chestcavity.items;



import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.tigereye.chestcavity.ChestCavity;

import javax.annotation.Nullable;
import java.util.List;

public class CreeperAppendix extends Item {

    public CreeperAppendix() {
        super(new Properties().stacksTo(1).tab(ChestCavity.ORGAN_ITEM_GROUP));
    }

    @Override
    public void appendHoverText(ItemStack itemStack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag tooltipContext) {
        super.appendHoverText(itemStack,world,tooltip,tooltipContext);
        tooltip.add(new StringTextComponent("\u8FD9\u4F3C\u4E4E\u662F\u4E00\u4E2A\u5BFC\u706B\u7D22").withStyle(TextFormatting.ITALIC));
        tooltip.add(new StringTextComponent("\u5176\u672C\u8EAB\u6CA1\u6709\u4EFB\u4F55\u4F5C\u7528").withStyle(TextFormatting.ITALIC));
    }
}
