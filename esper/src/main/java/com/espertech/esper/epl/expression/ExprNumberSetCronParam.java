/*
 * *************************************************************************************
 *  Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 *  http://esper.codehaus.org                                                          *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.epl.expression;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.type.CronOperatorEnum;
import com.espertech.esper.type.CronParameter;
import com.espertech.esper.util.JavaClassHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Expression for a parameter within a crontab.
 * <p>
 * May have one subnode depending on the cron parameter type.
 */
public class ExprNumberSetCronParam extends ExprNodeBase implements ExprEvaluator
{
    private static final Log log = LogFactory.getLog(ExprNumberSetCronParam.class);

    private final CronOperatorEnum cronOperator;
    private transient ExprEvaluator evaluator;
    private static final long serialVersionUID = -1315999998249935318L;

    /**
     * Ctor.
     * @param cronOperator type of cron parameter
     */
    public ExprNumberSetCronParam(CronOperatorEnum cronOperator)
    {
        this.cronOperator = cronOperator;
    }

    public ExprEvaluator getExprEvaluator()
    {
        return this;
    }

    /**
     * Returns the cron parameter type.
     * @return type of cron parameter
     */
    public CronOperatorEnum getCronOperator()
    {
        return cronOperator;
    }

    public String toExpressionString()
    {
        if (this.getChildNodes().length == 0)
        {
            return cronOperator.getSyntax();
        }
        return this.getChildNodes()[0].toExpressionString() + " " + cronOperator.getSyntax();
    }

    public boolean isConstantResult()
    {
        if (this.getChildNodes().length == 0)
        {
            return true;
        }
        return this.getChildNodes()[0].isConstantResult();
    }

    public boolean equalsNode(ExprNode node)
    {
        if (!(node instanceof ExprNumberSetCronParam))
        {
            return false;
        }
        ExprNumberSetCronParam other = (ExprNumberSetCronParam) node;
        return other.cronOperator.equals(this.cronOperator);
    }

    public void validate(ExprValidationContext validationContext) throws ExprValidationException
    {
        if (this.getChildNodes().length == 0)
        {
            return;
        }
        evaluator = this.getChildNodes()[0].getExprEvaluator();
        Class type = evaluator.getType();
        if (!(JavaClassHelper.isNumericNonFP(type)))
        {
            throw new ExprValidationException("Frequency operator requires an integer-type parameter");
        }
    }

    public Class getType()
    {
        return CronParameter.class;
    }

    public Object evaluate(EventBean[] eventsPerStream, boolean isNewData, ExprEvaluatorContext exprEvaluatorContext)
    {
        if (this.getChildNodes().length == 0)
        {
            return new CronParameter(cronOperator, null);
        }
        Object value = evaluator.evaluate(eventsPerStream, isNewData, exprEvaluatorContext);
        if (value == null)
        {
            log.warn("Null value returned for cron parameter");
            return new CronParameter(cronOperator, null);
        }
        else
        {
            int intValue = ((Number) value).intValue();
            return new CronParameter(cronOperator, intValue);
        }
    }
}
