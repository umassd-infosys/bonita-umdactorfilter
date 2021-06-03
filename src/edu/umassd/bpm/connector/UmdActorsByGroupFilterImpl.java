/**
 * 
 */
package edu.umassd.bpm.connector;

import java.util.ArrayList;
import java.util.List;

import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.engine.exception.SearchException;
import org.bonitasoft.engine.filter.UserFilterException;
import org.bonitasoft.engine.identity.Group;
import org.bonitasoft.engine.identity.GroupSearchDescriptor;
import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.identity.UserSearchDescriptor;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import java.util.logging.Logger;


/**
*The actor filter execution will follow the steps
* 1 - setInputParameters() --> the actor filter receives input parameters values
* 2 - validateInputParameters() --> the actor filter can validate input parameters values
* 3 - filter(final String actorName) --> execute the user filter
* 4 - shouldAutoAssignTaskIfSingleResult() --> auto-assign the task if filter returns a single result
*/
public class UmdActorsByGroupFilterImpl extends AbstractUmdActorsByGroupFilterImpl {

	@Override
	public void validateInputParameters() throws ConnectorValidationException {
		//TODO validate input parameters here 
	
	}

	@Override
	public List<Long> filter(final String actorName) throws UserFilterException {
		
		String groupName = (String) getInputParameter("groupName");
		String defaultGroupName = (String) getInputParameter("defaultGroup");
		Logger logger=Logger.getLogger("org.bonitasoft");
		logger.severe("Get Actors with group of: "+groupName);
		logger.severe("BackUp/Alternate group is: "+defaultGroupName);
		
		
		List<Long> userIds = new ArrayList<Long>();
		Long defaultUserId = (long) 1; //Hopefully never used -- just so the actor filter doesn't blow up
		List<Group> groups = null;
		final IdentityAPI identityAPI = this.getAPIAccessor().getIdentityAPI();
		SearchOptionsBuilder builder = new SearchOptionsBuilder(0, 1000);
		
		builder.filter(GroupSearchDescriptor.NAME, groupName);
		SearchResult<Group> groupResults;
		try {
			groupResults = identityAPI.searchGroups(builder.done());
			groups = groupResults.getResult();
		} catch (SearchException e) {
			// TODO Auto-generated catch block
			logger.severe(e.getMessage());
		}
		if(groups==null || groups.size()==0 ) {
			logger.severe("No groups found for "+groupName+"-- Using default group....");
			builder = new SearchOptionsBuilder(0, 1000);
			builder.filter(GroupSearchDescriptor.NAME, defaultGroupName);
			try {
				groupResults = identityAPI.searchGroups(builder.done());
				groups = groupResults.getResult();
			} catch (SearchException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			logger.severe("Group has "+groups.size()+ " results");
		}
		/* if we got here and groups is still null then something is wrong! */
		if(groups==null) {
			return null;
		}
		//TODO execute the user filter here
		//The method must return a list of user id's
		//you can use getApiAccessor() and getExecutionContext()
		
		for(Group g: groups) {
			builder = new SearchOptionsBuilder(0, 5000);
			builder.filter(UserSearchDescriptor.GROUP_ID, g.getId());
			logger.severe("Get users for groupId "+g.getId());
			SearchResult<User> userResults;
			try {
				userResults = identityAPI.searchUsers(builder.done());
				List<User> users = userResults.getResult();
				logger.severe(users.size() + " users returned from query");
				for(User u: users) {
					userIds.add(u.getId());
				}
			} catch (SearchException e) {
				//Get the default group.... 
				
			}	

		}
		if(userIds.size()==0) {
			logger.severe("Adding default user ID of "+defaultUserId.toString());
			userIds.add(defaultUserId);
		} else {
			logger.severe(userIds.size() + " entries being returned!");
		}
		return userIds;
	}

	@Override
	public boolean shouldAutoAssignTaskIfSingleResult() {
		// If this method returns true, the step will be assigned to 
		//the user if there is only one result returned by the filter method
	     final Boolean autoAssignO = (Boolean) getInputParameter("autoAssign");
	     return autoAssignO == null ? true : autoAssignO;
	}

}
