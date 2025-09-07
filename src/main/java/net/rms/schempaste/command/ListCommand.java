package net.rms.schempaste.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.rms.schempaste.config.PlacementConfig;
import net.rms.schempaste.core.SchematicIndex;

import java.io.IOException;

public class ListCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, java.nio.file.Path configDir, SchematicIndex index) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("sp")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.literal("list").executes(ctx -> execute(ctx.getSource(), configDir, index)));
        
        dispatcher.register(root);
    }
    
    private static int execute(ServerCommandSource src, java.nio.file.Path configDir, SchematicIndex index) {
        PlacementConfig cfg;
        try {
            cfg = PlacementConfig.load(configDir);
        } catch (IOException e) {
            //#if MC < 12000
            src.sendError(new net.minecraft.text.LiteralText("Failed to load placements.json: " + e.getMessage()));
            //#else
            //$$ src.sendError(net.minecraft.text.Text.literal("Failed to load placements.json: " + e.getMessage()));
            //#endif
            return 0;
        }
        
        java.util.List<PlacementConfig.Placement> items = new java.util.ArrayList<>();
        for (PlacementConfig.Placement p : cfg.placements) {
            if (p != null && p.fileName != null && !p.fileName.isEmpty()) {
                items.add(p);
            }
        }
        
        //#if MC < 12000
        src.sendFeedback(new net.minecraft.text.LiteralText("Available placements: " + items.size()), false);
        //#else
        //$$ src.sendFeedback(() -> net.minecraft.text.Text.literal("Available placements: " + items.size()), false);
        //#endif
        for (int i = 0; i < items.size(); i++) {
            String display = items.get(i).fileName;
            int number = i + 1;
            //#if MC < 12000
            src.sendFeedback(new net.minecraft.text.LiteralText(number + ". " + display), false);
            //#else
            //$$ src.sendFeedback(() -> net.minecraft.text.Text.literal(number + ". " + display), false);
            //#endif
        }
        return 1;
    }
}
