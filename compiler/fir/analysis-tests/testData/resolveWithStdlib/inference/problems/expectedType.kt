abstract class FirProperty {
    abstract val returnTypeRef: FirTypeRef
}

abstract class FirTypeRef

abstract class FirResolvedTypeRef : FirTypeRef() {
    abstract val type: ConeKotlinType
}

abstract class ConeKotlinType

inline fun <reified C : ConeKotlinType> FirTypeRef.coneTypeSafe(): C? {
    return (this as? FirResolvedTypeRef)?.type as? C
}

class Session(val property: FirProperty) {
    val expectedType: ConeKotlinType? by lazy { property.returnTypeRef.coneTypeSafe() }
}
