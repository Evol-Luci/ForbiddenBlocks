package me.lucievol.forbiddenblocks;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.ModInitializer;
import me.lucievol.forbiddenblocks.config.ForbiddenBlocksConfig;

public class ForbiddenBlocks implements ModInitializer {

	@Override
	public void onInitialize() {


	}


}

class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ForbiddenBlocksConfig::createConfigScreen;
	}
}