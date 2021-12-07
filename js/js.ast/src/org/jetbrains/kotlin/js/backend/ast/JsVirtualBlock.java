/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.backend.ast;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsVirtualBlock extends JsBlock {
    public JsVirtualBlock(@NotNull List<JsStatement> statements) { super(statements); }
}
