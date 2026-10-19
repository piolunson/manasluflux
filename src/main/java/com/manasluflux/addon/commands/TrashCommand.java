package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;

public class TrashCommand extends Command {
    public TrashCommand() {
        super("mftrash", "Confirms dropping your entire inventory. Run 'mftrash confirm' to actually drop.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }
            int count = 0;
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty()) count++;
            }
            Msg.warning("This would drop %d item stacks! Run 'mftrash confirm' to drop your whole inventory.", count);
            return SINGLE_SUCCESS;
        });

        builder.then(literal("confirm").executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    mc.player.dropItem(stack, true);
                    stack.setCount(0);
                }
            }
            Msg.info("Inventory dropped.");
            return SINGLE_SUCCESS;
        }));
    }
}
