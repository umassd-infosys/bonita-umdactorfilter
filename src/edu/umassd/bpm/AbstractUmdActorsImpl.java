/**
 * 
 */
package edu.umassd.bpm;

import org.bonitasoft.engine.filter.AbstractUserFilter;
import org.bonitasoft.engine.connector.ConnectorValidationException;

/**
 * This abstract class is generated and should not be modified.
 */
public abstract class AbstractUmdActorsImpl extends AbstractUserFilter {

	protected final static String ACTORTYPE_INPUT_PARAMETER = "actorType";
	protected final static String STRINGVALUE_INPUT_PARAMETER = "stringValue";
	protected final static String ALLOWDELEGATION_INPUT_PARAMETER = "allowDelegation";
	protected final static String AUTOASSIGN_INPUT_PARAMETER = "autoAssign";
	protected final static String FALLBACKVALUE_INPUT_PARAMETER = "fallbackValue";
	protected final static String FALLBACKUSER_INPUT_PARAMETER = "fallbackUser";

	protected final java.lang.String getActorType() {
		return (java.lang.String) getInputParameter(ACTORTYPE_INPUT_PARAMETER);
	}

	protected final java.lang.String getStringValue() {
		return (java.lang.String) getInputParameter(STRINGVALUE_INPUT_PARAMETER);
	}

	protected final java.lang.Boolean getAllowDelegation() {
		return (java.lang.Boolean) getInputParameter(ALLOWDELEGATION_INPUT_PARAMETER);
	}

	protected final java.lang.Boolean getAutoAssign() {
		return (java.lang.Boolean) getInputParameter(AUTOASSIGN_INPUT_PARAMETER);
	}

	protected final java.lang.String getFallbackValue() {
		return (java.lang.String) getInputParameter(FALLBACKVALUE_INPUT_PARAMETER);
	}

	protected final java.lang.String getFallbackUser() {
		return (java.lang.String) getInputParameter(FALLBACKUSER_INPUT_PARAMETER);
	}

	@Override
	public void validateInputParameters() throws ConnectorValidationException {
		try {
			getActorType();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("actorType type is invalid");
		}
		try {
			getStringValue();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("stringValue type is invalid");
		}
		try {
			getAllowDelegation();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("allowDelegation type is invalid");
		}
		try {
			getAutoAssign();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("autoAssign type is invalid");
		}
		try {
			getFallbackValue();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("fallbackValue type is invalid");
		}
		try {
			getFallbackUser();
		} catch (ClassCastException cce) {
			throw new ConnectorValidationException("fallbackUser type is invalid");
		}

	}

}
