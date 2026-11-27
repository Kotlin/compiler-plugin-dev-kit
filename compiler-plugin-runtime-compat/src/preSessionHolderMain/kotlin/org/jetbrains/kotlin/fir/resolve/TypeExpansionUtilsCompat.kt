package org.jetbrains.kotlin.fir.resolve

import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeRigidType
import org.jetbrains.kotlin.fir.types.ConeSimpleKotlinType

context(sessionHolder: SessionHolder)
public fun ConeClassLikeType.fullyExpandedType(): ConeClassLikeType =
    fullyExpandedType(sessionHolder.session)

context(sessionHolder: SessionHolder)
public fun ConeKotlinType.fullyExpandedType(): ConeKotlinType =
    fullyExpandedType(sessionHolder.session)

context(sessionHolder: SessionHolder)
public fun ConeSimpleKotlinType.fullyExpandedType(): ConeSimpleKotlinType =
    fullyExpandedType(sessionHolder.session)

context(sessionHolder: SessionHolder)
public fun ConeRigidType.fullyExpandedType(): ConeRigidType =
    fullyExpandedType(sessionHolder.session)
