package com.espertech.esper.eql.view;

import com.espertech.esper.collection.MultiKey;
import com.espertech.esper.collection.Pair;
import com.espertech.esper.core.StatementContext;
import com.espertech.esper.eql.core.ResultSetProcessor;
import com.espertech.esper.eql.spec.OutputLimitSpec;
import com.espertech.esper.event.EventBean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

/**
 * A view that handles the "output snapshot" keyword in output rate stabilizing.
 */
public class OutputProcessViewSnapshot extends OutputProcessView
{
    private final OutputCondition outputCondition;

	private static final Log log = LogFactory.getLog(OutputProcessViewSnapshot.class);

    /**
     * Ctor.
     * @param resultSetProcessor is processing the result set for publishing it out
     * @param streamCount is the number of streams, indicates whether or not this view participates in a join
     * @param outputLimitSpec is the specification for limiting output (the output condition and the result set processor)
     * @param statementContext is the services the output condition may depend on
     * @param isInsertInto is true if the statement is a insert-into
     * @param outputStrategy is the method to use to produce output
     */
    public OutputProcessViewSnapshot(ResultSetProcessor resultSetProcessor,
                          OutputStrategy outputStrategy,
                          boolean isInsertInto,
                          int streamCount,
    					  OutputLimitSpec outputLimitSpec,
    					  StatementContext statementContext)
    {
        super(resultSetProcessor, outputStrategy, isInsertInto, statementContext.getStatementResultService());
        log.debug(".ctor");

    	if(streamCount < 1)
    	{
    		throw new IllegalArgumentException("Output process view is part of at least 1 stream");
    	}

    	OutputCallback outputCallback = getCallbackToLocal(streamCount);
    	this.outputCondition = statementContext.getOutputConditionFactory().createCondition(outputLimitSpec, statementContext, outputCallback);
    }

    /**
     * The update method is called if the view does not participate in a join.
     * @param newData - new events
     * @param oldData - old events
     */
    public void update(EventBean[] newData, EventBean[] oldData)
    {
        if (log.isDebugEnabled())
        {
            log.debug(".update Received update, " +
                    "  newData.length==" + ((newData == null) ? 0 : newData.length) +
                    "  oldData.length==" + ((oldData == null) ? 0 : oldData.length));
        }

        resultSetProcessor.processViewResult(newData, oldData, false);

        // add the incoming events to the event batches
        int newDataLength = 0;
        int oldDataLength = 0;
        if(newData != null)
        {
        	newDataLength = newData.length;
        }
        if(oldData != null)
        {
        	oldDataLength = oldData.length;
        }

        outputCondition.updateOutputCondition(newDataLength, oldDataLength);
    }

    /**
     * This process (update) method is for participation in a join.
     * @param newEvents - new events
     * @param oldEvents - old events
     */
    public void process(Set<MultiKey<EventBean>> newEvents, Set<MultiKey<EventBean>> oldEvents)
    {
        if (log.isDebugEnabled())
        {
            log.debug(".process Received update, " +
                    "  newData.length==" + ((newEvents == null) ? 0 : newEvents.size()) +
                    "  oldData.length==" + ((oldEvents == null) ? 0 : oldEvents.size()));
        }

        resultSetProcessor.processJoinResult(newEvents, oldEvents, false);

        int newEventsSize = 0;
        if (newEvents != null)
        {
            // add the incoming events to the event batches
            newEventsSize = newEvents.size();
        }

        int oldEventsSize = 0;
        if (oldEvents != null)
        {
            oldEventsSize = oldEvents.size();
        }

        outputCondition.updateOutputCondition(newEventsSize, oldEventsSize);
    }

	/**
	 * Called once the output condition has been met.
	 * Invokes the result set processor.
	 * Used for non-join event data.
	 * @param doOutput - true if the batched events should actually be output as well as processed, false if they should just be processed
	 * @param forceUpdate - true if output should be made even when no updating events have arrived
	 * */
	protected void continueOutputProcessingView(boolean doOutput, boolean forceUpdate)
	{
		log.debug(".continueOutputProcessingView");

        EventBean[] newEvents = null;
        EventBean[] oldEvents = null;

        Iterator<EventBean> it = this.iterator();
        if (it.hasNext())
        {
            ArrayList<EventBean> snapshot = new ArrayList<EventBean>();
            for (EventBean bean : this)
            {
                snapshot.add(bean);
            }
            newEvents = snapshot.toArray(new EventBean[snapshot.size()]);
            oldEvents = null;
        }

        Pair<EventBean[], EventBean[]> newOldEvents = new Pair<EventBean[], EventBean[]>(newEvents, oldEvents);

        if(doOutput)
		{
			output(forceUpdate, newOldEvents);
		}
	}

	private void output(boolean forceUpdate, Pair<EventBean[], EventBean[]> results)
	{
        // Child view can be null in replay from named window
        if (childView != null)
        {
            outputStrategy.output(forceUpdate, results, childView);
        }
	}

	/**
	 * Called once the output condition has been met.
	 * Invokes the result set processor.
	 * Used for join event data.
	 * @param doOutput - true if the batched events should actually be output as well as processed, false if they should just be processed
	 * @param forceUpdate - true if output should be made even when no updating events have arrived
	 */
	protected void continueOutputProcessingJoin(boolean doOutput, boolean forceUpdate)
	{
		log.debug(".continueOutputProcessingJoin");
        continueOutputProcessingView(doOutput, forceUpdate);
	}

    private OutputCallback getCallbackToLocal(int streamCount)
    {
        // single stream means no join
        // multiple streams means a join
        if(streamCount == 1)
        {
            return new OutputCallback()
            {
                public void continueOutputProcessing(boolean doOutput, boolean forceUpdate)
                {
                    OutputProcessViewSnapshot.this.continueOutputProcessingView(doOutput, forceUpdate);
                }
            };
        }
        else
        {
            return new OutputCallback()
            {
                public void continueOutputProcessing(boolean doOutput, boolean forceUpdate)
                {
                    OutputProcessViewSnapshot.this.continueOutputProcessingJoin(doOutput, forceUpdate);
                }
            };
        }
    }
}
