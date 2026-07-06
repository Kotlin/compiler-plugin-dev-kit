package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.directives.model.DirectiveApplicability
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.directives.model.ValueDirective

abstract class DevKitDirectivesContainer : SimpleDirectivesContainer() {
  protected inline fun <reified T : Enum<T>> enumDirective(
    description: String,
    applicability: DirectiveApplicability = DirectiveApplicability.Global,
  ): DirectiveDelegateProvider<ValueDirective<T>> =
    enumDirective(description, applicability) { null }

  @JvmName("enumDirectiveWithAdditionalParser")
  protected inline fun <reified T : Enum<T>> enumDirective(
    description: String,
    applicability: DirectiveApplicability = DirectiveApplicability.Global,
    noinline additionalParser: (String) -> T?,
  ): DirectiveDelegateProvider<ValueDirective<T>> {
    val possibleValues = enumValues<T>()
    val parser: (String) -> T? = { value ->
      possibleValues.firstOrNull { it.name == value } ?: additionalParser(value)
    }
    return valueDirective(description, applicability, parser)
  }
}
