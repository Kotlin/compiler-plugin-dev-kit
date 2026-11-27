package __PACKAGE__

import java.net.URL
import java.util.Collections
import java.util.Enumeration
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

internal class DevKitRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = DevKitEntryPoint.PLUGIN_ID
    override val supportsK2: Boolean
        get() = true

    private val registrar: CompilerPluginRegistrar? by lazy {
        DevKitEntryPoint.registrar("__REGISTRAR_CLASS__")
    }

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        registrar?.run { registerExtensions(configuration) }
    }
}

internal class DevKitProcessor : CommandLineProcessor {
    override val pluginId: String = DevKitEntryPoint.PLUGIN_ID

    private val processor: CommandLineProcessor? by lazy {
        DevKitEntryPoint.processor("__PROCESSOR_CLASS__")
    }

    override val pluginOptions: Collection<AbstractCliOption>
        get() = processor?.pluginOptions ?: emptyList()

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        processor?.processOption(option, value, configuration)
    }
}

internal object DevKitEntryPoint {
    internal const val PLUGIN_ID = "__PLUGIN_ID__"

    private const val CLASS_LOADER_ID = "__CLASS_LOADER_ID__"

    private const val ENTRY_POINT_DIR = "META-INF/kotlin/plugin/$CLASS_LOADER_ID/entryPoint/"

    private const val REGISTRAR_CLASS =
        "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPluginRegistrar"

    private const val PROCESSOR_CLASS =
        "org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCommandLineProcessor"

    private val entryPointClassLoader: ClassLoader? by
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val anchorLoader = DevKitEntryPoint::class.java.classLoader ?: return@lazy null
            EntryPointClassLoader(anchorLoader, anchorLoader.parent)
        }

    internal fun registrar(registrarClass: String): CompilerPluginRegistrar? =
        create(REGISTRAR_CLASS, registrarClass) as? CompilerPluginRegistrar

    internal fun processor(processorClass: String): CommandLineProcessor? =
        create(PROCESSOR_CLASS, processorClass) as? CommandLineProcessor

    private fun create(className: String, userClass: String): Any? {
        val loader = entryPointClassLoader ?: return null
        return try {
            loader
                .loadClass(className)
                .getDeclaredConstructor(Class::class.java, String::class.java, String::class.java)
                .newInstance(DevKitEntryPoint::class.java, CLASS_LOADER_ID, userClass)
        } catch (_: ClassNotFoundException) {
            error("Malformed JAR! Missing DevKit entry point class $className")
        }
    }

    private class EntryPointClassLoader(
        private val classPath: ClassLoader,
        parent: ClassLoader?,
    ) : ClassLoader(parent) {
        override fun findClass(name: String): Class<*> {
            val bytes =
                classPath
                    .getResourceAsStream("$ENTRY_POINT_DIR${name.replace('.', '/')}.class")
                    ?.use { it.readBytes() } ?: throw ClassNotFoundException(name)
            return defineClass(name, bytes, 0, bytes.size)
        }

        override fun findResource(name: String): URL? =
            classPath.getResources("$ENTRY_POINT_DIR$name").toList().firstOrNull()

        override fun findResources(name: String): Enumeration<URL> =
            Collections.enumeration(classPath.getResources("$ENTRY_POINT_DIR$name").toList())
    }
}
