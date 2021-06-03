package edu.umassd.bpm.connector;

import org.bonitasoft.engine.filter.AbstractUserFilter;
import org.bonitasoft.engine.connector.ConnectorValidationException;

public abstract class AbstractUmdActorsByGroupFilterImpl extends AbstractUserFilter {

	protected final static String GROUPNAME_INPUT_PARAMETER = "groupName";
	protected final static String DEFAULTGROUP_INPUT_PARAMETER = "defaultGroup";
	protected final static String AUTOASSIGN_INPUT_PARAMETER = "autoAssign";

	protected final java.lang.String getGroupName() {
		return (java.lang.String) getInputParameter(GROUPNAME_INPUT_PARAMETER);
	}

	protected final java.lang.String getDefaultGroup() {
		return (java.lang.String) getInputParameter(DEFAULTGROUP_INPUT_PARAMETER);
	}

	protected final java.lang.Boolean getAutoAssign() {
		return (java.lang.Boolean) getInputParameter(AUTOASSIGN_INPUT_PARAMETER);
	}

	@Override
	public void validateInputParameters() throws ConnectorValidationException {
		try {
			getGroupName();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("groupName type is invalid");
		}
		try {
			getDefaultGroup();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("defaultGroup type is invalid");
		}
		try {
			getAutoAssign();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("autoAssign type is invalid");
		}

	}

}
