package de.jlo.talendcomp.gamanage;

import com.google.api.services.analytics.model.Goal;
import com.google.api.services.analytics.model.Goal.EventDetails.EventConditions;

public class GoalEventConditionWrapper {

	public Goal goal;
	public int index;
	public EventConditions condition;
}