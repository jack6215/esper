/**************************************************************************************
 * Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 * http://esper.codehaus.org                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.client.soda;

import java.io.StringWriter;

public class PatternExprPlaceholder extends PatternExprBase
{
    /**
     * Ctor.
     */
    public PatternExprPlaceholder()
    {
    }

    public void toPrecedenceFreeEPL(StringWriter writer) {
        if ((this.getChildren() == null) || (this.getChildren().size() == 0)) {
            return;
        }
        PatternExpr patternExpr = getChildren().get(0);
        if (patternExpr != null) {
            patternExpr.toEPL(writer, getPrecedence());
        }
    }

    public PatternExprPrecedenceEnum getPrecedence() {
        return PatternExprPrecedenceEnum.MINIMUM;
    }
}