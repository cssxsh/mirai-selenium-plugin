package xyz.cssxsh.mirai.test;

import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;
import xyz.cssxsh.mirai.selenium.MiraiSeleniumPlugin;
import xyz.cssxsh.selenium.RemoteWebDriverConfig;
import xyz.cssxsh.selenium.SeleniumToolKt;

import java.io.File;

class MiraiSeleniumPluginJavaTest extends JavaPlugin {
    public MiraiSeleniumPluginJavaTest() {
        super(new JvmPluginDescriptionBuilder("xyz.cssxsh.mirai.plugin.mirai-selenium-plugin-test", "0.0.0")
                .dependsOn("xyz.cssxsh.mirai.plugin.mirai-selenium-plugin", false)
                .build());
    }

    /**
     * RemoteWebDriverConfig 是接口，可以自己定义
     */
    private final RemoteWebDriverConfig config = RemoteWebDriverConfig.INSTANCE;


    @Override
    public void onEnable() {
        Runnable runnable = () -> {
            RemoteWebDriver driver = MiraiSeleniumPlugin.INSTANCE.driver(config);

            long current = System.currentTimeMillis();
            while (true) {
                if (SeleniumToolKt.isReady(driver)) {
                    break;
                }
                if (current - System.currentTimeMillis() > 100_000) {
                    break;
                }
            }

            File screenshot = driver.getScreenshotAs(OutputType.FILE);

            driver.close();
        };


        getScheduler().async(runnable);
    }
}
