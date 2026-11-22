package org.jetbrains.kotlin.ir.builders

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.parentAsClass

// inlined versions of irConstructorCall and irCall are used here
// so that `IrBuilder` can be used instead of `IrBuilderWithScope`
// because the functions use `IrBuilderWithScope` before `2.2.0-dev-12428`
public fun IrBuilder.irAnnotation(
    callee: IrConstructorSymbol,
    typeArguments: List<IrType>,
): IrAnnotation =
    IrConstructorCallImpl.fromSymbolOwner(
            startOffset,
            endOffset,
            callee.owner.returnType,
            callee,
            typeArguments.size - callee.owner.typeParameters.size,
        )
        .apply {
            typeArguments.forEachIndexed { index, irType ->
                this.typeArguments[index] = irType
            }
        }

public fun IrBuilder.irAnnotation(
    callee: IrConstructorSymbol,
    type: IrType,
    constructedClass: IrClass = callee.owner.parentAsClass,
): IrAnnotation =
    IrConstructorCallImpl(
        startOffset,
        endOffset,
        type,
        callee,
        typeArgumentsCount =
            callee.owner.typeParameters.size + constructedClass.typeParameters.size,
        constructorTypeArgumentsCount = callee.owner.typeParameters.size,
    )

public fun IrBuilder.irAnnotation(callee: IrConstructorSymbol): IrAnnotation =
    irAnnotation(callee, callee.owner.returnType)
