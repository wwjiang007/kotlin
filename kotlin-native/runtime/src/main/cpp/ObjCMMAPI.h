/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_OBJCMMAPI_H
#define RUNTIME_OBJCMMAPI_H

#include "Common.h"

#if KONAN_OBJC_INTEROP

extern "C" void Kotlin_ObjCExport_releaseAssociatedObject(void* associatedObject);

#endif // KONAN_OBJC_INTEROP

#endif // RUNTIME_OBJCMMAPI_H
