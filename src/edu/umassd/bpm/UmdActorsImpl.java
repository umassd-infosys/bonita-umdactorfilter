/**
 * 
 */
package edu.umassd.bpm;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.bonitasoft.engine.api.APIClient;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bdm.BusinessObjectDaoCreationException;
import org.bonitasoft.engine.bpm.actor.ActorCriterion;
import org.bonitasoft.engine.bpm.actor.ActorInstance;
import org.bonitasoft.engine.bpm.actor.ActorMember;
import org.bonitasoft.engine.bpm.actor.ActorNotFoundException;
import org.bonitasoft.engine.bpm.bar.actorMapping.Actor;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ActivityInstanceNotFoundException;
import org.bonitasoft.engine.bpm.process.ProcessDefinition;
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException;
import org.bonitasoft.engine.business.data.BusinessDataRepositoryException;
import org.bonitasoft.engine.connector.ConnectorAPIAccessorImpl;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.engine.connector.EngineExecutionContext;
import org.bonitasoft.engine.exception.SearchException;
import org.bonitasoft.engine.filter.UserFilterException;
import org.bonitasoft.engine.identity.Group;
import org.bonitasoft.engine.identity.GroupSearchDescriptor;
import org.bonitasoft.engine.identity.Role;
import org.bonitasoft.engine.identity.RoleNotFoundException;
import org.bonitasoft.engine.identity.RoleSearchDescriptor;
import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.identity.UserNotFoundException;
import org.bonitasoft.engine.identity.UserSearchDescriptor;
import org.bonitasoft.engine.io.IOUtils;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.bonitasoft.engine.service.ModelConvertor;
import org.bonitasoft.engine.service.TenantServiceAccessor;
import org.bonitasoft.engine.service.TenantServiceSingleton;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.session.SessionService;
import org.bonitasoft.engine.session.model.SSession;
import org.bonitasoft.engine.sessionaccessor.SessionAccessor;

import edu.umassd.model.HRDeanDepartmentToGroup;
import edu.umassd.model.HRDeanDepartmentToGroupDAO;
import edu.umassd.model.HRDepartmentToChair;
import edu.umassd.model.HRDepartmentToChairDAO;
import edu.umassd.model.HRDepartments;
import edu.umassd.model.HRDepartmentsDAO;
import edu.umassd.model.HREmployeeJobRecords;
import edu.umassd.model.HREmployees;
import edu.umassd.model.HREmployeesDAO;
import edu.umassd.model.SAAcademicPlans;
import edu.umassd.model.SAAcademicPlansDAO;
import edu.umassd.model.SASubjectCodes;
import edu.umassd.model.SASubjectCodesDAO;
import edu.umassd.model.UMDDelegation;
import edu.umassd.model.UMDDelegationDAO;

/**
*The actor filter execution will follow the steps
* 1 - setInputParameters() --> the actor filter receives input parameters values
* 2 - validateInputParameters() --> the actor filter can validate input parameters values
* 3 - filter(final String actorName) --> execute the user filter
* 4 - shouldAutoAssignTaskIfSingleResult() --> auto-assign the task if filter returns a single result
*/
public class UmdActorsImpl extends AbstractUmdActorsImpl {

	//help us get more information about the context of what is going on
	private Long defaultUserId=1L;
	private Long tenantId=1L;
	private Long taskId;
	private Long processDefinitionId;
	//Maximum number of candidates to return in a search for actor members
	private int maxNumberOfCandidates = 500;
	private EngineExecutionContext engineExecutionContext; 
	private Logger logger=Logger.getLogger("org.bonitasoft");
	private IdentityAPI identityAPI;
	private ProcessAPI processAPI;
	private ActivityInstance activity;
	private ProcessDefinition processDefinition;
	private List<Long> userIds;
	private APIClient apiClient=null;
	private APISession apiSession=null;
    private final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
	private File clientFolder = new File(System.getProperty("catalina.base")+"/umdBDM");
	private List<URL> jarURLs = new ArrayList<URL>();


	@Override
	public void validateInputParameters() throws ConnectorValidationException {
		super.validateInputParameters();
	}
	
	/* Filtering criteria to load in a JAR or not */
	public static class BDMJarFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String name) {
			if(name.toLowerCase().endsWith("jar")) {
				if(name.contains("model") || name.contains("dao")) {
					return true;
				}
			}
			return false;
		}
	}
	/* do the Jars we need to load already exist? */
	public boolean canLoadJARs() throws MalformedURLException {
		FilenameFilter filter = new BDMJarFilter();
		File[] files = clientFolder.listFiles(filter);
		int found=0;
		//How many jars are we expecting to find?
		int toExpect=2;
		for(File file : files) {
            jarURLs.add(file.toURI().toURL());
            found++;
		}
		if(found==toExpect) {
			return true;
		} 
		//Empty out our JAR urls
		jarURLs.clear();
		return false;
	}
	/* Jars don't exist, so extract the BDM information from the Engine -- place the jar files in clientFolder */
	public boolean createJARs() throws IOException {
		byte[] clientBDMZip=null;
		try {
			clientBDMZip = apiClient.getTenantAdministrationAPI().getClientBDMZip();
			final Map<String, byte[]> zipFile = IOUtils.unzip(clientBDMZip);
			for (final Entry<String, byte[]> e : zipFile.entrySet()) {
				final File file = new File(clientFolder, e.getKey());
				if (file.getName().endsWith(".jar")) {
					if (file.getName().contains("model") || file.getName().contains("dao")) {
						FileUtils.writeByteArrayToFile(file, e.getValue());
						jarURLs.add(file.toURI().toURL());
					}
				}
			}
		} catch (BusinessDataRepositoryException e1) {
			logger.severe("Could not initialize BDM information");
			return false;
		}
		return true;
	}
	
	public void log(String message) {
		logger.info(message);
	}
	
	/* Override the existing class loader to point to the BDM jars 
	 * 
	 * It is important to release these jars later because they conflict with what is already in Bonita's memory (Why?!?)
	 */
	public boolean setClassLoader() {
		try {
			if(clientFolder.mkdir()) {
				log("created new folder");
			}
	        ClassLoader classLoaderWithBDM = originalClassLoader;
			/* Check to see if the BDM jars already exist in clientFoler */
	        if(canLoadJARs()!=true) {
	        	if(createJARs()!=true) {
	        		log("could not create or locate bdm jars!");
	        		return false;
	        	}
	        }
	        if (!jarURLs.isEmpty()) {
	        	log(jarURLs.toString());
	            classLoaderWithBDM = new URLClassLoader(jarURLs.toArray(new URL[jarURLs.size()]), originalClassLoader);
	            Thread.currentThread().setContextClassLoader(classLoaderWithBDM);
	            return true;
	        } else {
	        	log("Could not fetch class loader! :( ");
	        	return false;
	        }
		} catch (IOException e) {
			logger.info("Could not create folder "+clientFolder.getName());
			return false;
		}
	}

	
	private boolean doRoute(final String valueToLookup) throws UserFilterException {
		this.engineExecutionContext = this.getExecutionContext();
		this.taskId = engineExecutionContext.getActivityInstanceId();
		this.processDefinitionId = engineExecutionContext.getProcessDefinitionId();
		this.identityAPI = this.getAPIAccessor().getIdentityAPI();
		this.processAPI = this.getAPIAccessor().getProcessAPI();
		this.userIds = new ArrayList<Long>();
		String actorClassName = null;
		String stringValue = null;
		Long longValue = null;
		
		try {
			this.activity = processAPI.getActivityInstance(this.taskId);
		} catch (ActivityInstanceNotFoundException e) {
			// Couldn't fetch activity instance
			log("Could not locate activity instance for "+this.taskId.toString());
			return false;
		}
		try {
			this.processDefinition = processAPI.getProcessDefinition(this.processDefinitionId);
		} catch (ProcessDefinitionNotFoundException e) {
			// could not fetch process definition
			log("Could not fetch process definition for "+this.processDefinitionId.toString());
			return false;
		}

		/* Determine which path we're taking... */
		log("Parsing "+getActorType()+"; value of: "+valueToLookup);
		String[] actorTypeParts = getActorType().split("-",3);
		String actorMethod = actorTypeParts[0].trim();
		String actorExpectedValue = actorTypeParts[1].trim();
		String actorExpectedObject = actorTypeParts[2].trim();
		if(actorExpectedObject.equalsIgnoreCase("String")) {
			stringValue = valueToLookup;
			actorClassName = "String";
			if(stringValue==null) {
				throw new UserFilterException("String value not passed in!");
			}
		} else if(actorExpectedObject.equalsIgnoreCase("Long")) {
			try {
			longValue = Long.parseLong(valueToLookup);
			} catch (Exception e) {
				log("Could not parse the Long value of "+valueToLookup);
				return false;
			}
			actorClassName = "Long";
		} else {
			throw new UserFilterException("Expected Object Type not Passed!");
		}

		if(!actorExpectedObject.equalsIgnoreCase(actorClassName)) {
			log("Object passed to filter does not match expected type of "+actorExpectedObject);
			return false;
		}
		log("Expected value "+ actorExpectedValue);
		switch(actorMethod) {
			/* Identity Based Routes */
			case "Group":
				if(actorExpectedValue.equalsIgnoreCase("Group Id")) {
					doGroupFilter(longValue); 
				} else {
					doGroupFilter(stringValue);
				}
				break;
			case "User":
				if(actorExpectedValue.equalsIgnoreCase("User Id")) {
					doUserFilter(longValue);
				} else {
					doUserFilter(stringValue);
				}
				break;
			case "User List": doUserCSVFilter(stringValue); break;
			case "Actor Members": doActorMemberFilter(stringValue); break;
			case "Role Members" : doRoleFilter(stringValue); break;
			
			/* DAO-Based Routes */
			case "Supervisor": doSupervisorFilter(longValue);break;
			case "Plan Chairperson": doPlanChairFilter(stringValue); break;
			case "Plan Program Director": doPlanDirectorFilter(stringValue); break;
			case "Subject Chairperson": doSubjectChairFilter(stringValue); break;
			case "Plan Dean":  doPlanDeanFilter(stringValue); break;
			case "Subject Dean": doSubjectDeanFilter(stringValue); break;
			case "HR Chairperson": doHRDepartmentChairFilter(stringValue); break;
			case "HR Dean": doHRDeanDepartmentFilter(stringValue); break;
			case "Division Head": doHRDivisionHeadFilter(stringValue); break;

			default: return false;
		}
		
		return false;
	}
	
	public void doRoleFilter(String roleName) {
		List<Role> roles = null;
		SearchOptionsBuilder builder = new SearchOptionsBuilder(0, maxNumberOfCandidates);
		builder.filter(RoleSearchDescriptor.NAME, roleName);
		SearchResult<Role> roleResults;
		try {
			roleResults = identityAPI.searchRoles(builder.done());
			roles = roleResults.getResult();
		} catch (SearchException e) {
			logger.severe(e.getMessage());
		}
		for(Role r : roles) {
			doRoleFilter(r.getId());
		}		

	}
	
	public void doRoleFilter(Long roleId) {
		try {
			Role role = identityAPI.getRole(roleId);
			SearchOptionsBuilder builder = new SearchOptionsBuilder(0, maxNumberOfCandidates);
			builder.filter(UserSearchDescriptor.ROLE_ID, role.getId());
			log("Get users for role "+role.getId());
			SearchResult<User> userResults;
			try {
				userResults = identityAPI.searchUsers(builder.done());
				List<User> users = userResults.getResult();
				log(users.size() + " users returned from query");
				for(User u: users) {
					userIds.add(u.getId());
				}
			} catch (SearchException e) {
				//Get the default group.... 
				
			}
		} catch (RoleNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	
	public void doActorMemberFilter(Long actorId) {
		
		ActorInstance actor;
		try {
			actor = processAPI.getActor(actorId);
			doActorMemberFilter(actor.getName());

		} catch (ActorNotFoundException e) {
			e.printStackTrace();
			logger.severe("Could not find actor with id of "+actorId.toString()); 
		}
		
	}
	public void doActorMemberFilter(String actorName) {
		log("Going to look at actor named "+actorName);
		
		userIds.addAll( processAPI.getUserIdsForActor(processDefinitionId, actorName, 0, maxNumberOfCandidates) );
	}
	
	public boolean getAPIClient() {
		if(this.apiClient == null) {
			final TenantServiceAccessor tenantServiceAccessor = TenantServiceSingleton.getInstance(tenantId);
			final SessionAccessor sessionAccessor = tenantServiceAccessor.getSessionAccessor();
			final SessionService sessionService = tenantServiceAccessor.getSessionService();
			try {
				final SSession session = sessionService.createSession(tenantId,ConnectorAPIAccessorImpl.class.getSimpleName());
				sessionAccessor.setSessionInfo(session.getId(), tenantId);
				this.apiSession = ModelConvertor.toAPISession(session, null);
				this.apiClient = new APIClient(this.apiSession);
			} catch(Exception ex) {
				logger.info("Could not get session!");
				logger.info(ex.getMessage());
			}
	
		}
		if(this.apiClient!=null) {
			return true;
		}
		return false;
	}
	
	public List<Long> filter(final String actorName) throws UserFilterException {
		if(!getAPIClient()) {
			throw new UserFilterException("Could not initialize apiClient");
		}
		
		setClassLoader();

		if( !doRoute(getStringValue() )) {
			//bad, but we can use fallback
		}
		
		/* if we have a fallback value mapped, then evaluate that */
		String fallBack = getFallbackValue();
		if(fallBack!=null && userIds.isEmpty()) {
			log("Re-evaluating with new fallback value of "+fallBack);
			if(!doRoute(fallBack)) {
				//bad, but we can use defaultUser
			}
		}
		if(userIds.isEmpty()) {
			log(" No user results -- setting to default user");
			userIds.add(getDefaultUser());
		}
		
		if(getAllowDelegation() == true) {
			log("Run through delegation matrix!");
			doDelegation();
		}
		
		log(userIds.toString());
		
		//Set classLoader back to how we found it
        Thread.currentThread().setContextClassLoader(originalClassLoader);

		return userIds;	
	}

	private void doDelegation() {
		String activityName = activity.getName();
		String processName = processDefinition.getName();
		UMDDelegationDAO delegationDAO;
		log("Pre delegation users: "+userIds.toString());
		try {
			delegationDAO = apiClient.getDAO(UMDDelegationDAO.class);
			List<UMDDelegation> possibleDelegates = delegationDAO.findByProcess(processName, 0, 5000);
			/* Iterate through our current users */
			/* Add the delegates into a new List & then add them into the main one once over to avoid concurrentmodificationexception */
			List<Long> newDelegates = new ArrayList<Long>();
			
			for(Long userId :userIds){
				for(UMDDelegation delegate : possibleDelegates) {
					if(userId == delegate.getUserId() && delegate.isDeleted()==false) {
						if((delegate.getTaskName().equals("ALL TASKS") || 
								delegate.getTaskName().equals(activityName)) && !userIds.contains(delegate.getDelegate())) {
							//OK to add!
							log("Adding delegate! ");
							newDelegates.add(delegate.getDelegate());
						}
					}
				}
			}
			/* add all the newDelegates back in */
			userIds.addAll(newDelegates);
		} catch (BusinessObjectDaoCreationException e) {
			logger.info("Exception getting delegates!");
			logger.info(e.getMessage());
			e.printStackTrace();
		}
		log("Added delegates, now: ...."+userIds.toString());
	}
	
	
	private Long getDefaultUser() {
		try {
			log("Fall Back Value: "+getFallbackValue());
			User u = identityAPI.getUserByUserName(getFallbackUser());
			return u.getId();
		} catch(Exception ex) {
			log("Trying default user: Could not locate "+getFallbackUser());
		}
		return this.defaultUserId;

	}
	
	/* Given a Plan name, fetch the associated Dean-level approvers */
	public void doPlanDeanFilter(final String planName) {
		SAAcademicPlansDAO plansDAO;
		SAAcademicPlans plan=null;
		HRDepartments deanDept=null;
		HRDepartmentsDAO hrDepartmentsDAO;

		try {
				
			plansDAO = apiClient.getDAO(SAAcademicPlansDAO.class);						
			plan = plansDAO.findByAc_plan(planName);
			hrDepartmentsDAO = apiClient.getDAO(HRDepartmentsDAO.class);						
						
			if(plan!=null && plan.getDeanGroupId() != null) {
				doGroupFilter(plan.getDeanGroupId());
			} else if(plan!=null && plan.getDeanDepartmentId() != null) {
				doHRDeanDepartmentFilter(plan.getDeanDepartmentId());
			} else if(plan!=null) {
				//Fall back to HRDeanDepartment record
				deanDept = hrDepartmentsDAO.findByDeptId(plan.getAc_org_hrid()).getDeanDepartment();
				doHRDeanDepartmentFilter(deanDept.getDeptId());
			}
		} catch (BusinessObjectDaoCreationException e) {
			logger.info("Could not establish DAO for SAAcademicPlansDAO");
		}
	}
	
	public void doSubjectDeanFilter(final String subjectCode) {
		SASubjectCodesDAO subjectDAO;
		HRDepartmentsDAO hrDepartmentsDAO;
		HRDepartments deanDept=null;
		SASubjectCodes subject=null;
		try {
			subjectDAO = apiClient.getDAO(SASubjectCodesDAO.class);
			hrDepartmentsDAO = apiClient.getDAO(HRDepartmentsDAO.class);						

			subject = subjectDAO.findBySubjectCode(subjectCode);
			log(subject.toString());
						
			if(subject!=null && subject.getDeanGroupId() != null) {
				doGroupFilter(subject.getDeanGroupId());
			} else if(subject != null && subject.getDeanDepartmentId() != null) {
				doHRDeanDepartmentFilter(subject.getDeanDepartmentId());
			} else if(subject!=null){
				//Fall back to HRDeanDepartment record
				//Get the current 
				deanDept = hrDepartmentsDAO.findByDeptId(subject.getAc_org_hrid()).getDeanDepartment();
				doHRDeanDepartmentFilter(deanDept.getDeptId());
			} 
		} catch (BusinessObjectDaoCreationException e) {
			logger.info("Could not establish DAO for SASubjectCodesDAO");
		}
	}	

	public void doPlanDirectorFilter(final String planName) {
		
		SAAcademicPlansDAO plansDAO;
		SAAcademicPlans plan=null;
		
		try {
			plansDAO = apiClient.getDAO(SAAcademicPlansDAO.class);
			plan = plansDAO.findByAc_plan(planName);
			if(plan !=null && plan.getGpdUserId() != null) {
				userIds.add(lookupUser(plan.getGpdUserId()));
			} else {
				//fall back to the chairperson
				doPlanChairFilter(planName);
			}
		} catch (BusinessObjectDaoCreationException e) {
			logger.info("Could not establish DAO for SAAcademicPlansDAO - doPlanDirector");
		}
	}
	
	public void doPlanChairFilter(final String planName) {
				
		SAAcademicPlansDAO plansDAO;
		SAAcademicPlans plan=null;
		
		try {
			plansDAO = apiClient.getDAO(SAAcademicPlansDAO.class);
			plan = plansDAO.findByAc_plan(planName);
			if(plan !=null && plan.getChairGroupId() != null) {
				doGroupFilter(plan.getChairGroupId());
			} else if(plan!= null && plan.getChairUserId() != null) {
				userIds.add(lookupUser(plan.getChairUserId()));
			} else if(plan!=null){
				//Fall back to HRDepartmentToChair record
				doHRDepartmentChairFilter(plan.getAc_org_hrid());
			}
		} catch (BusinessObjectDaoCreationException e) {
			logger.info("Could not establish DAO for SAAcademicPlansDAO - doPlanChair");
		}
	}
	
	public void doSubjectChairFilter(final String subjectCode) {
		SASubjectCodesDAO subjectDAO;
		SASubjectCodes subject;
		try {
			subjectDAO = apiClient.getDAO(SASubjectCodesDAO.class);
			subject = subjectDAO.findBySubjectCode(subjectCode);
			if(subject!=null && subject.getChairGroupId() != null) {
				doGroupFilter(subject.getChairGroupId());
				log("got group chair id");
			} else if(subject!= null && subject.getChairUserId() != null) {
				userIds.add(lookupUser(subject.getChairUserId()));
				log("got chair user id");
			} else if(subject!=null) {
				//Fall back to HRDepartmentToChair record
				doHRDepartmentChairFilter(subject.getAc_org_hrid());
				log("falling back to HRDepartmentChair");
			}
		} catch (BusinessObjectDaoCreationException e) {
			logger.info("Could not establish DAO for SASubjectPlansDAO");
		}
	}
	
	public void doHRDepartmentChairFilter(final String departmentId) {
		HRDepartmentToChairDAO deptDAO;
		HRDepartmentToChair dept;
		Long userId;
		try {
			deptDAO = apiClient.getDAO(HRDepartmentToChairDAO.class);
			dept = deptDAO.findByDepartmentId(departmentId, 0, 1).get(0);
			if(dept!=null) {
				userId = dept.getUserId();
				if(userId!=null && userId>0) {
					userIds.add(userId);
				} else if(dept.getGroupId()!=null) {
					doGroupFilter(dept.getGroupId());
				}
			}
		} catch(Exception ex) {
			
		}		
	}
	
	/* Pass in a departmentId -- assume it is looking for the dean for this department 
	 * 
	 * But may be the HRDepartmentId OF the Dean Department and we just want the approval group
	 * 
	 * 
	 * */
	public void doHRDeanDepartmentFilter(final String departmentId) {
		HRDeanDepartmentToGroupDAO deanDAO;
		HRDeanDepartmentToGroup dean;
		HRDepartmentsDAO deptDAO;
		HRDepartments dept;
		boolean satisfied = false;
		try {
			deptDAO = apiClient.getDAO(HRDepartmentsDAO.class);
			deanDAO = apiClient.getDAO(HRDeanDepartmentToGroupDAO.class);
			dept = deptDAO.findByDeptId(departmentId);
			if(dept!=null && dept.getDeanDepartmentId()!=null && !dept.getDeanDepartmentId().equalsIgnoreCase(departmentId)) {
				dean = deanDAO.findByDepartmentId(dept.getDeanDepartmentId());
				if(dean!=null && dean.getGroupId()!=null) {
					satisfied=true;
					doGroupFilter(dean.getGroupId());
				}
			}
			if(satisfied==false) {
				dean = deanDAO.findByDepartmentId(departmentId);
				log("doing dean filter");
				log(departmentId);
				if(dean==null) {
					log("could not locate dean");
				}
				if(dean!=null && dean.getGroupId()!=null) {
					doGroupFilter(dean.getGroupId());
				}
			}
		} catch(Exception ex) {
			logger.info("Exception with HRDeanDepartmentFilter: "+departmentId);
		}
		
	}
	
	
	public void doUserFilter(String userName) {
		try {
			User user = identityAPI.getUserByUserName(userName);
			doUserFilter( user.getId() );
		} catch (UserNotFoundException e) {
			log("Could not fetch user "+userName);
		}
	}
	
	public void doUserFilter(Long userid) {
		userIds.add(userid);
	}
	/* Given a string CSV that has a comma or semicolon delimiter, get the user Ids */
	public void doUserCSVFilter(String userString) {
		String[] users = userString.split(",|;");
				
		for(String u : users) {
			try {
				userIds.add( Long.parseLong(u.trim())  );
			} catch(Exception ex) {
				logger.severe("Could not add value "+u+" to UserCSVFilter");
			}
		}		
	}

	public void doSupervisorFilter(Long userId) {
		Long supervisorId;
		User user,supervisor=null;
		String supervisorUsername;
		HREmployeesDAO hrDAO;
		HREmployees hr;

		try {
			user = identityAPI.getUser(userId);
			hrDAO = apiClient.getDAO(HREmployeesDAO.class);
			hr = hrDAO.findByUsername(user.getUserName(), 0,10).get(0);
			List<HREmployeeJobRecords> jobs = hr.getJobs();
			for(HREmployeeJobRecords job : jobs) {
				try {
					supervisorUsername = job.getSupervisor().getUsername();
					supervisor = identityAPI.getUserByUserName(supervisorUsername);
					userIds.add(supervisor.getId());
				} catch(Exception ex) {
					//supervisor doesn't exist in bonita, don't worry about it!
				}
			}
			try {
				supervisorId = user.getManagerUserId();
				if(supervisorId!=null) {
					userIds.add(supervisorId);
				}
			} catch(Exception ex) {
				//bonita doesn't know the supervisor
			}
		} catch(Exception ex) {
			logger.info("Passed in userId "+userId+" does not exist!");
		}
	}
	
	public Long lookupUser(Long userId) {
		try {
			User u = identityAPI.getUser(userId);
			if(u.isEnabled()) {
				return u.getId();
			}
		} catch(Exception ex) {
			logger.info("Passed in userId "+userId+" does not exist!");
		}
		return this.defaultUserId;
	}
	
	/* Given a group Id, execute group filter */
	public void doGroupFilter(final Long groupId) {
		try {
			Group g = identityAPI.getGroup(groupId);
			doGroupFilter(g);
		} catch(Exception ex) {
			logger.info("Exception with doGroupIdFilter group lookup for groupId "+groupId.toString());
		}
	}
	
	/* Given a string, do a group Filter */
	public void doGroupFilter(final String groupName) {
		List<Group> groups = null;

		SearchOptionsBuilder builder = new SearchOptionsBuilder(0, maxNumberOfCandidates);
		builder.filter(GroupSearchDescriptor.NAME, groupName);
		SearchResult<Group> groupResults;
		try {
			groupResults = identityAPI.searchGroups(builder.done());
			groups = groupResults.getResult();
		} catch (SearchException e) {
			logger.severe(e.getMessage());
		}
		for(Group g : groups) {
			doGroupFilter(g);
		}
	}
	
	/* Pass a Group in -- get users */
	public void doGroupFilter(final Group group) {
		SearchOptionsBuilder builder = new SearchOptionsBuilder(0, maxNumberOfCandidates);
		builder.filter(UserSearchDescriptor.GROUP_ID, group.getId());
		log("Get users for groupId "+group.getId());
		SearchResult<User> userResults;
		try {
			userResults = identityAPI.searchUsers(builder.done());
			List<User> users = userResults.getResult();
			log(users.size() + " users returned from query");
			for(User u: users) {
				userIds.add(u.getId());
			}
		} catch (SearchException e) {
			//Get the default group.... 
			
		}
	}
	
	/* Pass in a department Id -- get the division head approver from HRDeanToDepartment */
	public void doHRDivisionHeadFilter(final String departmentId) {
		HRDeanDepartmentToGroupDAO recordDAO;
		HRDeanDepartmentToGroup record;
		HRDepartmentsDAO deptDAO;
		HRDepartments dept;
		try {
			deptDAO = apiClient.getDAO(HRDepartmentsDAO.class);
			dept = deptDAO.findByDeptId(departmentId);
			recordDAO = apiClient.getDAO(HRDeanDepartmentToGroupDAO.class);

			if(dept!=null && dept.getDivisionDepartmentId()!=null) {
				record = recordDAO.findByDepartmentId(dept.getDivisionDepartmentId());
				if(record!=null) {
					doHRDeanDepartmentFilter(record.getDepartmentId() );
				}
			} 
		} catch(Exception e) {
			
		}
	}
	
	@Override
	public boolean shouldAutoAssignTaskIfSingleResult() {
		// If this method returns true, the step will be assigned to 
		//the user if there is only one result returned by the filter method
		return getAutoAssign();	
	}

}
