package org.jetbrains.kotlin.ir.builders

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.parentAsClass

public fun IrBuilder.irAnnotation(
    callee: IrConstructorSymbol,
    typeArguments: List<IrType>,
): IrAnnotation = irCallConstructor(callee, typeArguments)

public fun IrBuilder.irAnnotation(
    callee: IrConstructorSymbol,
    type: IrType,
    constructedClass: IrClass = callee.owner.parentAsClass,
): IrAnnotation = irCall(callee, type, constructedClass)

public fun IrBuilder.irAnnotation(callee: IrConstructorSymbol): IrAnnotation =
    irAnnotation(callee, callee.owner.returnType)
