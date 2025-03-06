package me.lucievol.forbiddenblocks.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

@Config(name = "forbiddenblocks")
public class ForbiddenBlocksConfig implements ConfigData {
    @ConfigEntry.Gui.Excluded
    public static KeyBinding forbidKey = new KeyBinding(
            "key.forbiddenblocks.forbid",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,  // This sets the default keybinding
            "category.forbiddenblocks"
    );

    public static void init() {
        AutoConfig.register(ForbiddenBlocksConfig.class, Toml4jConfigSerializer::new);
    }

    public static net.minecraft.client.gui.screen.Screen createConfigScreen(net.minecraft.client.gui.screen.Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.of("Forbidden Blocks Settings"));

        ConfigCategory general = builder.getOrCreateCategory(Text.of("Keybindings"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder.startKeyCodeField(Text.of("Forbid Item Key"), forbidKey.getDefaultKey())
                .setKeySaveConsumer(code -> forbidKey.setBoundKey(InputUtil.fromKeyCode(InputUtil.Type.KEYSYM.ordinal(), code.getCode())))  // Fix here
                .build());

        return builder.build();
    }
}
