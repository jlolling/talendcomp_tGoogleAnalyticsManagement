package de.jlo.talendcomp.gamanage;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.AnalyticsRequest;
import com.google.api.services.analytics.AnalyticsScopes;
import com.google.api.services.analytics.model.Account;
import com.google.api.services.analytics.model.Accounts;
import com.google.api.services.analytics.model.Column;
import com.google.api.services.analytics.model.Columns;
import com.google.api.services.analytics.model.EntityUserLink;
import com.google.api.services.analytics.model.EntityUserLinks;
import com.google.api.services.analytics.model.Goal;
import com.google.api.services.analytics.model.Goal.EventDetails;
import com.google.api.services.analytics.model.Goal.EventDetails.EventConditions;
import com.google.api.services.analytics.model.Goal.UrlDestinationDetails;
import com.google.api.services.analytics.model.Goal.UrlDestinationDetails.Steps;
import com.google.api.services.analytics.model.CustomDataSource;
import com.google.api.services.analytics.model.CustomDataSources;
import com.google.api.services.analytics.model.Goals;
import com.google.api.services.analytics.model.Profile;
import com.google.api.services.analytics.model.Profiles;
import com.google.api.services.analytics.model.Segment;
import com.google.api.services.analytics.model.Segments;
import com.google.api.services.analytics.model.UnsampledReport;
import com.google.api.services.analytics.model.UnsampledReports;
import com.google.api.services.analytics.model.Webproperties;
import com.google.api.services.analytics.model.Webproperty;

public class GoogleAnalyticsManagement {

	private Logger logger = null;
	private static final Map<String, GoogleAnalyticsManagement> clientCache = new HashMap<String, GoogleAnalyticsManagement>();
	private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private final JsonFactory JSON_FACTORY = new JacksonFactory();
	private File keyFile; // *.p12 key file is needed
	private String accountEmail;
	private String applicationName = null;
	private boolean useServiceAccount = true;
	private String credentialDataStoreDir = null;
	private String clientSecretFile = null;
	private int timeoutInSeconds = 120;
	private Analytics analyticsClient;
	private long timeMillisOffsetToPast = 10000;
	private List<Account> listAccounts;
	private List<Webproperty> listWebProperties;
	private List<Profile> listProfiles;
	private List<Segment> listSegments;
	private List<Goal> listGoals;
	private List<ProfileUserPermission> listUserLinksForProfiles;
	private List<WebPropertyUserPermission> listUserLinksForWebProperties;
	private List<AccountUserPermission> listUserLinksForAccounts;
	private List<GoalUrlDestinationStepWrapper> listGoalUrlDestinationSteps;
	private List<GoalEventConditionWrapper> listGoalEventConditions;
	private List<Column> listColumns;
	private List<UnsampledReport> listUnsampledReports;
	private List<CustomDataSource> listCustomDataSources;
	private long innerLoopWaitInterval = 500;
	private int maxRows = 0;
	private int currentIndex = 0;
	private boolean ignoreUserPermissionErrors = false;
	
	public static void putIntoCache(String key, GoogleAnalyticsManagement gam) {
		clientCache.put(key, gam);
	}
	
	public static GoogleAnalyticsManagement getFromCache(String key) {
		return clientCache.get(key);
	}
	
	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public void setKeyFile(String file) {
		keyFile = new File(file);
	}

	public void setAccountEmail(String email) {
		accountEmail = email;
	}

	public void setTimeoutInSeconds(int timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
	}
	
	private Credential authorizeWithServiceAccount() throws Exception {
		if (keyFile == null) {
			throw new Exception("KeyFile not set!");
		}
		if (keyFile.canRead() == false) {
			throw new IOException("keyFile:" + keyFile.getAbsolutePath()
					+ " is not readable");
		}
		if (accountEmail == null || accountEmail.isEmpty()) {
			throw new Exception("account email cannot be null or empty");
		}
		// Authorization.
		return new GoogleCredential.Builder()
				.setTransport(HTTP_TRANSPORT)
				.setJsonFactory(JSON_FACTORY)
				.setServiceAccountId(accountEmail)
				.setServiceAccountScopes(Arrays.asList(AnalyticsScopes.ANALYTICS_READONLY, AnalyticsScopes.ANALYTICS_MANAGE_USERS))
				.setServiceAccountPrivateKeyFromP12File(keyFile)
				.setClock(new Clock() {
					@Override
					public long currentTimeMillis() {
						// we must be sure, that we are always in the past from Googles point of view
						// otherwise we get an "invalid_grant" error
						return System.currentTimeMillis() - timeMillisOffsetToPast;
					}
				})
				.build();
	}

	/**
	 * Authorizes the installed application to access user's protected YouTube
	 * data.
	 * 
	 * @param scopes
	 *            list of scopes needed to access general and analytic YouTube
	 *            info.
	 */
	private Credential authorizeWithClientSecret() throws Exception {
		if (clientSecretFile == null) {
			throw new IllegalStateException("client secret file is not set");
		}
		File secretFile = new File(clientSecretFile);
		if (secretFile.exists() == false) {
			throw new Exception("Client secret file:" + secretFile.getAbsolutePath() + " does not exists or is not readable.");
		}
		Reader reader = new FileReader(secretFile);
		// Load client secrets.
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
		try {
			reader.close();
		} catch (Throwable e) {}
		// Checks that the defaults have been replaced (Default =
		// "Enter X here").
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret()
						.startsWith("Enter ")) {
			throw new Exception("The client secret file does not contains the credentials. At first you have to pass the web based authorization process!");
		}
		credentialDataStoreDir = secretFile.getParent() + "/" + clientSecrets.getDetails().getClientId() + "/";
		File credentialDataStoreDirFile = new File(credentialDataStoreDir);             
		if (credentialDataStoreDirFile.exists() == false && credentialDataStoreDirFile.mkdirs() == false) {
			throw new Exception("Credentedial data dir does not exists or cannot created:" + credentialDataStoreDir);
		}
		FileDataStoreFactory fdsf = new FileDataStoreFactory(credentialDataStoreDirFile);
		// Set up authorization code flow.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				HTTP_TRANSPORT, 
				JSON_FACTORY, 
				clientSecrets, 
				Arrays.asList(AnalyticsScopes.ANALYTICS_READONLY))
			.setDataStoreFactory(fdsf)
			.setClock(new Clock() {
				@Override
				public long currentTimeMillis() {
					// we must be sure, that we are always in the past from Googles point of view
					// otherwise we get an "invalid_grant" error
					return System.currentTimeMillis() - timeMillisOffsetToPast;
				}
			})
			.build();
		// Authorize.
		return new AuthorizationCodeInstalledApp(
				flow,
				new LocalServerReceiver()).authorize(accountEmail);
	}

	public void initializeAnalyticsClient() throws Exception {
		// Authorization.
		final Credential credential;
		if (useServiceAccount) {
			credential = authorizeWithServiceAccount();
		} else {
			credential = authorizeWithClientSecret();
		}
		// Set up and return Google Analytics API client.
		analyticsClient = new Analytics.Builder(
			HTTP_TRANSPORT, 
			JSON_FACTORY, 
			new HttpRequestInitializer() {
				@Override
				public void initialize(final HttpRequest httpRequest) throws IOException {
					credential.initialize(httpRequest);
					httpRequest.setConnectTimeout(timeoutInSeconds * 1000);
					httpRequest.setReadTimeout(timeoutInSeconds * 1000);
				}
			})
			.setApplicationName(applicationName)
			.build();
	}
	
	public void reset() {
		listAccounts = null;
		listWebProperties = null;
		listProfiles = null;
		listSegments = null;
		listGoals = null;
		listGoalEventConditions = null;
		listGoalUrlDestinationSteps = null;
		listUnsampledReports = null;
		listUserLinksForProfiles = null;
		listUserLinksForWebProperties = null;
		listUserLinksForAccounts = null;
		listCustomDataSources = null;
		maxRows = 0;
		currentIndex = 0;
	}

	public boolean isUseServiceAccount() {
		return useServiceAccount;
	}

	public void setUseServiceAccount(boolean useServiceAccount) {
		this.useServiceAccount = useServiceAccount;
	}

	public String getClientSecretFile() {
		return clientSecretFile;
	}

	public void setClientSecretFile(String clientSecretFile) {
		this.clientSecretFile = clientSecretFile;
	}

	public boolean isIgnoreUserPermissionErrors() {
		return ignoreUserPermissionErrors;
	}

	public void setIgnoreUserPermissionErrors(boolean ignoreUserPermissionErrors) {
		this.ignoreUserPermissionErrors = ignoreUserPermissionErrors;
	}

	private void setMaxRows(int rows) {
		if (maxRows < rows) {
			maxRows = rows;
		}
	}
	
	private int maxRetriesInCaseOfErrors = 5;
	private int currentAttempt = 0;
	private int errorCode = 0;
	private String errorMessage = null;

	private com.google.api.client.json.GenericJson execute(AnalyticsRequest<?> request) throws IOException {
		com.google.api.client.json.GenericJson response = null;
		int waitTime = 1000;
		for (currentAttempt = 0; currentAttempt < maxRetriesInCaseOfErrors; currentAttempt++) {
			errorCode = 0;
			try {
				response = (GenericJson) request.execute();
				break;
			} catch (IOException ge) {
				boolean isPermissionError = false;
				if (ge instanceof GoogleJsonResponseException) {
					 GoogleJsonError gje = ((GoogleJsonResponseException) ge).getDetails();
					 if (gje != null) {
						 List<ErrorInfo> errors = gje.getErrors();
						 if (errors != null && errors.isEmpty() == false) {
							 ErrorInfo ei = errors.get(0);
							 if ("insufficientPermissions".equalsIgnoreCase(ei.getReason())) {
								 isPermissionError = true;
							 }
						 }
					 }
				}
				if (isPermissionError) {
					if (ignoreUserPermissionErrors) {
						info("Permission error ignored. Element skipped.");
						break;
					} else {
						throw ge; // it does not makes sense to repeat request which fails because of permissions
					}
				} else {
					if (ge instanceof HttpResponseException) {
						errorCode = ((HttpResponseException) ge).getStatusCode();
					}
					warn("Got error:" + ge.getMessage());
					if (currentAttempt == (maxRetriesInCaseOfErrors - 1)) {
						error("All repetition of requests failed:" + ge.getMessage(), ge);
						throw ge;
					} else {
						// wait
						try {
							info("Retry request in " + waitTime + "ms");
							Thread.sleep(waitTime);
						} catch (InterruptedException ie) {}
						waitTime = waitTime * 2;
					}
				}
			}
			try {
				Thread.sleep(innerLoopWaitInterval);
			} catch (InterruptedException e) {
				break;
			}
		}
		return response;
	}
	
	public void collectAccounts() throws IOException {
		info("Collect accounts...");
		listAccounts = new ArrayList<Account>();
		Accounts accounts = (Accounts) execute(
				analyticsClient
				.management()
				.accounts()
				.list());
		if (accounts != null) {
			for (Account account : accounts.getItems()) {
				listAccounts.add(account);
			}
		}
		setMaxRows(listAccounts.size());
	}
	
	public void collectWebProperties() throws Exception {
		if (listAccounts == null) {
			collectAccounts();
		}
		info("Collect web properties...");
		listWebProperties = new ArrayList<Webproperty>();
		for (Account account : listAccounts) {
			info("* account: " + account.getId());
			Webproperties webproperties = (Webproperties) execute(
					analyticsClient
					.management()
					.webproperties()
					.list(account.getId()));
			if (webproperties != null) {
				for (Webproperty webproperty : webproperties.getItems()) {
					listWebProperties.add(webproperty);
				}
			}
		}
		setMaxRows(listWebProperties.size());
	}
	
	public void collectProfiles() throws Exception {
		if (listWebProperties == null) {
			collectWebProperties();
		}
		info("Collect views (profiles)...");
		listProfiles = new ArrayList<Profile>();
		for (Webproperty webproperty : listWebProperties) {
			info("* web property: " + webproperty.getId());
			final Profiles profiles = (Profiles) execute(
					analyticsClient
					.management()
					.profiles()
					.list(webproperty.getAccountId(), webproperty.getId()));
			if (profiles != null) {
				final List<Profile> listProfilesForWebproperty = profiles.getItems();
				if (listProfilesForWebproperty != null) {
					final int countProfiles = listProfilesForWebproperty.size();
					for (int i = 0; i < countProfiles; i++) {
						final Profile profile = listProfilesForWebproperty.get(i);
						if (profile != null) {
							listProfiles.add(profile);
						}
					}
				}
			}
		}
		setMaxRows(listProfiles.size());
	}
	
	public void collectGoals() throws Exception {
		if (listProfiles == null) {
			collectProfiles();
		}
		info("Collect goals...");
		listGoals = new ArrayList<Goal>();
		for (Profile profile : listProfiles) {
			info("* view: " + profile.getId());
			Goals goals = (Goals) execute(
					analyticsClient
					.management()
					.goals()
					.list(profile.getAccountId(), profile.getWebPropertyId(), profile.getId()));
			if (goals != null) {
				List<Goal> list = goals.getItems();
				if (list != null) {
					for (Goal goal : list) {
						listGoals.add(goal);
					}
				}
			}
		}
		setMaxRows(listGoals.size());
		collectGoalUrlDestinationSteps();
		collectGoalEventConditions();
	}
	
	private void collectGoalUrlDestinationSteps() throws Exception {
		if (listGoals == null) {
			collectGoals();
		}
		info("Collect goal url destination steps...");
		listGoalUrlDestinationSteps = new ArrayList<GoalUrlDestinationStepWrapper>();
		for (Goal goal : listGoals) {
			info("* goal: " + goal.getId());
			UrlDestinationDetails urlDetails = goal.getUrlDestinationDetails();
			if (urlDetails != null && urlDetails.getSteps() != null) {
				int index = 0;
				for (Steps step : urlDetails.getSteps()) {
					GoalUrlDestinationStepWrapper w = new GoalUrlDestinationStepWrapper();
					w.goal = goal;
					w.index = index;
					w.step = step;
					listGoalUrlDestinationSteps.add(w);
					index++;
				}
			}
		}
		setMaxRows(listGoalUrlDestinationSteps.size());
	}

	private void collectGoalEventConditions() throws Exception {
		if (listGoals == null) {
			collectGoals();
		}
		info("Collect goal event conditions...");
		listGoalEventConditions = new ArrayList<GoalEventConditionWrapper>();
		for (Goal goal : listGoals) {
			info("* goal: " + goal.getId());
			EventDetails eventDetails = goal.getEventDetails();
			if (eventDetails != null && eventDetails.getEventConditions() != null) {
				int index = 0;
				for (EventConditions cond : eventDetails.getEventConditions()) {
					GoalEventConditionWrapper w = new GoalEventConditionWrapper();
					w.goal = goal;
					w.index = index;
					w.condition = cond;
					listGoalEventConditions.add(w);
					index++;
				}
			}
		}
		setMaxRows(listGoalEventConditions.size());
	}

	public void collectSegments() throws Exception {
		info("Collect segments...");
		listSegments = new ArrayList<Segment>();
		Segments segments = (Segments) execute(
				analyticsClient.management()
				.segments()
				.list());
		if (segments != null && segments.getItems() != null) {
			for (Segment segment : segments.getItems()) {
				listSegments.add(segment);
			}
		}
		setMaxRows(listSegments.size());
	}
	
	public void collectProfileUserPermissions() throws Exception {
		if (listProfiles == null) {
			collectProfiles();
		}
		info("Collect users permissions for views...");
		listUserLinksForProfiles = new ArrayList<ProfileUserPermission>();
		for (Profile p : listProfiles) {
			 info("* view: " + p.getId());
			 EntityUserLinks eul = (EntityUserLinks) execute(
					 analyticsClient.management()
						.profileUserLinks()
						.list(
								p.getAccountId(),
								p.getWebPropertyId(),
								p.getId()));
			 if (eul != null) {
				 for (EntityUserLink e : eul.getItems()) {
					 ProfileUserPermission u = new ProfileUserPermission();
					 u.setAccountId(Long.valueOf(p.getAccountId()));
					 u.setWebPropertyId(p.getWebPropertyId());
					 u.setProfileId(Long.valueOf(p.getId()));
					 u.setEmail(e.getUserRef().getEmail());
					 EntityUserLink.Permissions per = e.getPermissions();
					 if (per != null) {
						 u.setEffectivePermissions(per.getEffective());
						 u.setLocalPermissions(per.getLocal());
					 }
					 listUserLinksForProfiles.add(u);
				 }
			 }
		}
		setMaxRows(listUserLinksForProfiles.size());
	}

	public void collectWebPropertyUserPermissions() throws Exception {
		if (listWebProperties == null) {
			collectWebProperties();
		}
		info("Collect users permissions for web properties...");
		listUserLinksForWebProperties = new ArrayList<WebPropertyUserPermission>();
		for (Webproperty p : listWebProperties) {
			info("* web property: " + p.getId());
			EntityUserLinks eul = (EntityUserLinks) execute(
					analyticsClient
					.management().webpropertyUserLinks()
					.list(p.getAccountId(), p.getId()));
			if (eul != null) {
				for (EntityUserLink e : eul.getItems()) {
					WebPropertyUserPermission u = new WebPropertyUserPermission();
					u.setAccountId(Long.valueOf(p.getAccountId()));
					u.setWebPropertyId(p.getId());
					u.setEmail(e.getUserRef().getEmail());
					EntityUserLink.Permissions per = e.getPermissions();
					if (per != null) {
						u.setEffectivePermissions(per.getEffective());
						u.setLocalPermissions(per.getLocal());
					}
					listUserLinksForWebProperties.add(u);
				}
			}
		}
		setMaxRows(listUserLinksForWebProperties.size());
	}

	public void collectAccountUserPermissions() throws Exception {
		if (listAccounts == null) {
			collectAccounts();
		}
		info("Collect users permissions for accounts...");
		listUserLinksForAccounts = new ArrayList<AccountUserPermission>();
		for (Account a : listAccounts) {
			 info("* account: " + a.getId());
			 EntityUserLinks eul = (EntityUserLinks) execute(
						analyticsClient.management()
							.accountUserLinks()
							.list(a.getId()));
			 if (eul != null) {
				 for (EntityUserLink e : eul.getItems()) {
					 AccountUserPermission u = new AccountUserPermission();
					 u.setAccountId(Long.valueOf(a.getId()));
					 u.setEmail(e.getUserRef().getEmail());
					 EntityUserLink.Permissions per = e.getPermissions();
					 if (per != null) {
						 u.setEffectivePermissions(per.getEffective());
						 u.setLocalPermissions(per.getLocal());
					 }
					 listUserLinksForAccounts.add(u);
				 }
			 }
		}
		setMaxRows(listUserLinksForAccounts.size());
	}

	public void setTimeOffsetMillisToPast(long timeMillisOffsetToPast) {
		this.timeMillisOffsetToPast = timeMillisOffsetToPast;
	}

	public boolean next() {
		return ++currentIndex <= maxRows;
	}
	
	public int getMaxRows() {
		return maxRows;
	}
	
	public int getCurrentIndex() {
		return currentIndex - 1;
	}
	
	public boolean hasCurrentAccount() {
		if (listAccounts != null) {
			return currentIndex <= listAccounts.size();
		} else {
			return false;
		}
	}
	
	public Account getCurrentAccount() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listAccounts.size()) {
			return listAccounts.get(currentIndex - 1);
		} else {
			return null;
		}
	}
	
	public boolean hasCurrentWebproperty() {
		if (listWebProperties != null) {
			return currentIndex <= listWebProperties.size();
		} else {
			return false;
		}
	}

	public Webproperty getCurrentWebproperty() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listWebProperties.size()) {
			return listWebProperties.get(currentIndex - 1);
		} else {
			return null;
		}
	}
	
	public boolean hasCurrentGoal() {
		if (listGoals != null) {
			return currentIndex <= listGoals.size();
		} else {
			return false;
		}
	}
	
    public Goal getCurrentGoal() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listGoals.size()) {
			return listGoals.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public boolean hasCurrentGoalUrlDestinationStep() {
		if (listGoalUrlDestinationSteps != null) {
			return currentIndex <= listGoalUrlDestinationSteps.size();
		} else {
			return false;
		}
	}

	public GoalUrlDestinationStepWrapper getCurrentGoalUrlDestinationStep() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listGoalUrlDestinationSteps.size()) {
			return listGoalUrlDestinationSteps.get(currentIndex - 1);
		} else {
			return null;
		}
	}
    
	public boolean hasCurrentGoalEventCondition() {
		if (listGoalEventConditions != null) {
			return currentIndex <= listGoalEventConditions.size();
		} else {
			return false;
		}
	}

    public GoalEventConditionWrapper getCurrentGoalEventCondition() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listGoalEventConditions.size()) {
			return listGoalEventConditions.get(currentIndex - 1);
		} else {
			return null;
		}
	}

    public boolean hasCurrentProfile() {
		if (listProfiles != null) {
			return currentIndex <= listProfiles.size();
		} else {
			return false;
		}
	}

    public Profile getCurrentProfile() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listProfiles.size()) {
			return listProfiles.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public boolean hasCurrentSegment() {
		if (listSegments != null) {
			return currentIndex <= listSegments.size();
		} else {
			return false;
		}
	}

	public Segment getCurrentSegment() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listSegments.size()) {
			return listSegments.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public boolean hasCurrentProfileUserPermission() {
		if (listUserLinksForProfiles != null) {
			return currentIndex <= listUserLinksForProfiles.size();
		} else {
			return false;
		}
	}

	public ProfileUserPermission getCurrentProfileUserPermission() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listUserLinksForProfiles.size()) {
			return listUserLinksForProfiles.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public boolean hasCurrentWebPropertyUserPermission() {
		if (listUserLinksForWebProperties != null) {
			return currentIndex <= listUserLinksForWebProperties.size();
		} else {
			return false;
		}
	}

	public WebPropertyUserPermission getCurrentWebPropertyUserPermission() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listUserLinksForWebProperties.size()) {
			return listUserLinksForWebProperties.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public boolean hasCurrentAccountUserPermission() {
		if (listUserLinksForAccounts != null) {
			return currentIndex <= listUserLinksForAccounts.size();
		} else {
			return false;
		}
	}

	public AccountUserPermission getCurrentAccountUserPermission() {
		if (currentIndex == 0) {
			throw new IllegalStateException("call next before!");
		}
		if (currentIndex <= listUserLinksForAccounts.size()) {
			return listUserLinksForAccounts.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public void collectColumns() throws Exception {
		info("Collect metric and dimension metadata...");
		Columns columns = (Columns) execute(
				analyticsClient
				.metadata()
				.columns()
				.list("ga"));
		listColumns = columns.getItems();
		setMaxRows(listColumns.size());
	}
	
	public boolean hasCurrentColumn() {
		if (listColumns != null) {
			return currentIndex <= listColumns.size();
		} else {
			return false;
		}
	}
	
	public Column getCurrentColumn() {
		if (currentIndex == 0) {
			throw new IllegalStateException("Call collectColuns before!");
		}
		if (currentIndex <= listColumns.size()) {
			return listColumns.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public void collectUnsampledReports() throws Exception {
		if (listProfiles == null) {
			collectProfiles();
		}
		info("Collect unsampled reports...");
		listUnsampledReports = new ArrayList<UnsampledReport>();
		for (Profile profile : listProfiles) {
			info("* view: " + profile.getId());
			UnsampledReports reports = (UnsampledReports) execute(
					analyticsClient
					.management()
					.unsampledReports().list(
				      profile.getAccountId(),
				      profile.getWebPropertyId(),
				      profile.getId()));
			if (reports != null && reports.getItems() != null) {
				for (UnsampledReport report : reports.getItems()) {
					listUnsampledReports.add(report);
				}
				setMaxRows(listUnsampledReports.size());
			}
		}
	}
	
	public boolean hasCurrentUnsampledReport() {
		if (listUnsampledReports != null) {
			return currentIndex <= listUnsampledReports.size();
		} else {
			return false;
		}
	}
	
	public UnsampledReport getCurrentUnsampledReport() {
		if (currentIndex == 0) {
			throw new IllegalStateException("Call collectUnsampledReports before!");
		}
		if (currentIndex <= listUnsampledReports.size()) {
			return listUnsampledReports.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	public void collectCustomDataSources() throws Exception {
		if (listWebProperties == null) {
			collectWebProperties();
		}
		info("Collect custom data sources...");
		listCustomDataSources = new ArrayList<CustomDataSource>();
		for (Webproperty w : listWebProperties) {
			info("* web property: " + w.getId());
 			CustomDataSources dataSources = (CustomDataSources) execute(
 					analyticsClient
					.management()
					.customDataSources()
					.list(w.getAccountId(), w.getId()));
			if (dataSources != null && dataSources.getItems() != null) {
				for (CustomDataSource ds : dataSources.getItems()) {
					listCustomDataSources.add(ds);
				}
			}
		}
		setMaxRows(listCustomDataSources.size());
	}

	public boolean hasCurrentCustomDataSource() {
		if (listCustomDataSources != null) {
			return currentIndex <= listCustomDataSources.size();
		} else {
			return false;
		}
	}
	
	public CustomDataSource getCurrentCustomDataSource() {
		if (currentIndex == 0) {
			throw new IllegalStateException("Call next before!");
		}
		if (currentIndex <= listCustomDataSources.size()) {
			return listCustomDataSources.get(currentIndex - 1);
		} else {
			return null;
		}
	}

	/**
	 * builds a separated String from the list entries
	 * @param list
	 * @param separator
	 * @return the chained strings
	 */
	public static String buildChain(List<String> list, String separator) {
		if (list == null || list.isEmpty()) {
			return null;
		}
		boolean firstLoop = true;
		StringBuilder sb = new StringBuilder();
		for (String s : list) {
			if (firstLoop) {
				firstLoop = false;
			} else {
				sb.append(separator);
			}
			sb.append(s);
		}
		return sb.toString();
	}

	public void info(String message) {
		if (logger != null) {
			logger.info(message);
		} else {
			System.out.println("INFO:" + message);
		}
	}
	
	public void debug(String message) {
		if (logger != null) {
			logger.debug(message);
		} else {
			System.out.println("DEBUG:" + message);
		}
	}

	public void warn(String message) {
		if (logger != null) {
			logger.warn(message);
		} else {
			System.err.println("WARN:" + message);
		}
	}

	public void error(String message, Exception e) {
		if (logger != null) {
			logger.error(message, e);
		} else {
			System.err.println("ERROR:" + message);
		}
	}

	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	public void setInnerLoopWaitInterval(Number innerLoopWaitInterval) {
		if (innerLoopWaitInterval != null) {
			long value = innerLoopWaitInterval.longValue();
			if (value > 500l) {
				this.innerLoopWaitInterval = value;
			}
		}
	}
	
	public void setMaxRetriesInCaseOfErrors(Integer maxRetriesInCaseOfErrors) {
		if (maxRetriesInCaseOfErrors != null && maxRetriesInCaseOfErrors > 0) {
			this.maxRetriesInCaseOfErrors = maxRetriesInCaseOfErrors;
		}
	}

	public int getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

}