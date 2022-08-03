package xyz.cssxsh.mirai.test;

import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import org.icepear.echarts.Bar;
import org.icepear.echarts.serializer.EChartsSerializer;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;
import xyz.cssxsh.mirai.selenium.MiraiSeleniumPlugin;
import xyz.cssxsh.selenium.*;

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

            // option 是 json 数据，格式详见 https://echarts.apache.org/zh/option.html
            // 这里方便演示，使用了 maven 库 org.icepear.echarts:echarts-java:1.0.4 的功能，需要请自行引入
            Bar bar = new Bar()
                    .setLegend()
                    .setTooltip("item")
                    .addXAxis(new String[] { "Matcha Latte", "Milk Tea", "Cheese Cocoa", "Walnut Brownie" })
                    .addYAxis()
                    .addSeries("2015", new Number[] { 43.3, 83.1, 86.4, 72.4 })
                    .addSeries("2016", new Number[] { 85.8, 73.4, 65.2, 53.9 })
                    .addSeries("2017", new Number[] { 93.7, 55.1, 82.5, 39.1 });
            EChartsMeta meta = new EChartsMeta(
                    "100%",
                    "100%",
                    EChartsSerializer.toJson(bar.getOption()),
                    "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.2.2/echarts.min.js",
                    EChartsRenderer.canvas,
                    1000
            );
            String url = EChartsKt.echarts(driver, meta);
            byte[] bytes = EChartsKt.data(url).component2();

            driver.close();
        };


        getScheduler().async(runnable);
    }
}
