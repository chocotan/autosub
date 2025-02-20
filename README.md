## AI开发的自用打轴机

* 需java21和javafx17

问： 为什么是javafx17？
> 从javafx18开始不支持gtk2.0，导致输入法无法切换

问： 下一个GUI工具是否会选用javafx了？
> 不会，这货根本没人用，连个输入法都搞不定


请使用如下JVM参数启动

```
-Djdk.gtk.version=2
--module-path
"/home/choco/Dev/projects/javafx-sdk-17.0.14/lib"
--add-modules
javafx.controls,javafx.fxml
--add-exports
javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED
--add-exports
javafx.base/com.sun.javafx=ALL-UNNAMED
--add-exports
javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED
--add-exports
javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED
--add-exports
javafx.media/com.sun.media=ALL-UNNAMED
--add-exports
javafx.graphics/com.sun.javafx.util=ALL-UNNAMED
--add-exports
javafx.base/com.sun.javafx.reflect=ALL-UNNAMED
--add-modules=javafx.graphics,javafx.fxml,javafx.media
--add-reads
javafx.graphics=ALL-UNNAMED
--add-opens
javafx.controls/com.sun.javafx.charts=ALL-UNNAMED
--add-opens
javafx.controls/com.sun.javafx.scene.control.inputmap=ALL-UNNAMED
--add-opens
javafx.graphics/com.sun.javafx.iio=ALL-UNNAMED
--add-opens
javafx.graphics/com.sun.javafx.iio.common=ALL-UNNAMED
--add-opens
javafx.graphics/com.sun.javafx.css=ALL-UNNAMED
--add-opens
javafx.base/com.sun.javafx.runtime=ALL-UNNAMED
--add-exports
javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED

```
