package de.jlo.talendcomp.googleanalytics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.Analytics.Management;
import com.google.api.services.analytics.AnalyticsScopes;
import com.google.api.services.analytics.model.Account;
import com.google.api.services.analytics.model.Accounts;
import com.google.api.services.analytics.model.Goal;
import com.google.api.services.analytics.model.Goal.EventDetails;
import com.google.api.services.analytics.model.Goal.EventDetails.EventConditions;
import com.google.api.services.analytics.model.Goal.UrlDestinationDetails;
import com.google.api.services.analytics.model.Goal.UrlDestinationDetails.Steps;
import com.google.api.services.analytics.model.Column;
import com.google.api.services.analytics.model.Columns;
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

	private static final Map<String, GoogleAnalyticsManagement> clientCache = new HashMap<String, GoogleAnalyticsManagement>();
	private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private final JsonFactory JSON_FACTORY = new JacksonFactory();
	private File keyFile; // *.p12 key file is needed
	private String accountEmail;
	private String applicationName = null;
	private int timeoutInSeconds = 120;
	private Analytics analyticsClient;
	private long timeMillisOffsetToPast = 10000;
	private String proxyHost;
	private String proxyPort;
	private String proxyUser;
	private String proxyPassword;
	private List<Account> listAccounts;
	private List<Webproperty> listWebProperties;
	private List<Profile> listProfiles;
	private List<Segment> listSegments;
	private List<Goal> listGoals;
	private List<GoalUrlDestinationStepWrapper> listGoalUrlDestinationSteps;
	private List<GoalEventConditionWrapper> listGoalEventConditions;
	private List<Column> listColumns;
	private List<UnsampledReport> listUnsampledReports;
	private long mainWaitInterval = 2000;
	private long innerLoopWaitInterval = 500;
	private int maxRows = 0;
	private int currentIndex = 0;
	
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
	
	public void initializeAnalyticsClient() throws Exception {
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
		final GoogleCredential credential = new GoogleCredential.Builder()
				.setTransport(HTTP_TRANSPORT)
				.setJsonFactory(JSON_FACTORY)
				.setServiceAccountId(accountEmail)
				.setServiceAccountScopes(Arrays.asList(AnalyticsScopes.ANALYTICS_READONLY))
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
		if (proxyHost != null) {
			System.setProperty("http.proxyHost", proxyHost);
			System.setProperty("https.proxyHost", proxyHost);
		}
		if (proxyPort != null) {
			System.setProperty("http.proxyPort", proxyPort);
			System.setProperty("https.proxyPort", proxyPort);
		}
		String encodedProxyCredentials = null;
		if (proxyUser != null) {
			StringBuilder sb = new StringBuilder();
			sb.append(proxyUser);
			sb.append(":");
			sb.append(proxyPassword);
			encodedProxyCredentials = Base64.encodeToBase64String(sb.toString().getBytes());
		}
		final String base64encodedCredentials = encodedProxyCredentials != null ? "Basic " + encodedProxyCredentials : null;
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
					if (base64encodedCredentials != null) {
						httpRequest.getHeaders().put("Proxy-Authorization", base64encodedCredentials);
					}
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
		maxRows = 0;
		currentIndex = 0;
	}

	public void collectAll() throws Exception {
		collectAccounts();
		Thread.sleep(mainWaitInterval);
		collectWebProperties();
		Thread.sleep(mainWaitInterval);
		collectProfiles();
		Thread.sleep(mainWaitInterval);
		collectSegments();
		Thread.sleep(mainWaitInterval);
		collectGoals();
	}
	
	private void setMaxRows(int rows) {
		if (maxRows < rows) {
			maxRows = rows;
		}
	}
	
	public void collectAccounts() throws IOException {
		listAccounts = new ArrayList<Account>();
		Accounts accounts = analyticsClient.management().accounts().list().execute();
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
		listWebProperties = new ArrayList<Webproperty>();
		for (Account account : listAccounts) {
			Thread.sleep(innerLoopWaitInterval);
			Webproperties webproperties = analyticsClient
					.management()
					.webproperties()
					.list(account.getId())
					.execute();
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
		listProfiles = new ArrayList<Profile>();
		for (Webproperty webproperty : listWebProperties) {
			Thread.sleep(innerLoopWaitInterval);
			final Management man = analyticsClient.management();
			if (man == null) {
				throw new Exception("Got none management object");
			}
			final com.google.api.services.analytics.Analytics.Management.Profiles manProfiles = man.profiles();
			if (manProfiles == null) {
				throw new Exception("Got none management profiles");
			}
			final com.google.api.services.analytics.Analytics.Management.Profiles.List list = manProfiles.list(webproperty.getAccountId(), webproperty.getId());
			if (list == null) {
				throw new Exception("Got none Profiles.List");
			}
			final Profiles profiles = list.execute();
			if (profiles == null) {
				throw new Exception("Got none response from list request");
			}
			final List<Profile> listProfilesForWebproperty = profiles.getItems();
			if (listProfilesForWebproperty == null) {
				System.err.println("Got none list of profiles for web property id:" + webproperty.getId() + ", website url:" + webproperty.getWebsiteUrl());
			} else {
				final int countProfiles = listProfilesForWebproperty.size();
				for (int i = 0; i < countProfiles; i++) {
					final Profile profile = listProfilesForWebproperty.get(i);
					if (profile != null) {
						listProfiles.add(profile);
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
		listGoals = new ArrayList<Goal>();
		for (Profile profile : listProfiles) {
			Thread.sleep(innerLoopWaitInterval);
			Goals goals = analyticsClient.management()
					.goals()
					.list(profile.getAccountId(), profile.getWebPropertyId(), profile.getId())
					.execute();
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
		listGoalUrlDestinationSteps = new ArrayList<GoalUrlDestinationStepWrapper>();
		for (Goal goal : listGoals) {
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
		listGoalEventConditions = new ArrayList<GoalEventConditionWrapper>();
		for (Goal goal : listGoals) {
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
		listSegments = new ArrayList<Segment>();
		Segments segments = analyticsClient.management()
				.segments()
				.list()
				.execute();
		if (segments != null && segments.getItems() != null) {
			for (Segment segment : segments.getItems()) {
				listSegments.add(segment);
			}
		}
		setMaxRows(listSegments.size());
	}
	
	public void setTimeOffsetMillisToPast(long timeMillisOffsetToPast) {
		this.timeMillisOffsetToPast = timeMillisOffsetToPast;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		if (proxyHost != null && proxyHost.trim().isEmpty() == false) {
			this.proxyHost = proxyHost.trim();
		} else {
			this.proxyHost = null;
		}
	}

	public String getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(String proxyPort) {
		if (proxyPort != null && proxyPort.trim().isEmpty() == false) {
			this.proxyPort = proxyPort.trim();
		} else {
			this.proxyPort = null;
		}
	}

	public String getProxyUser() {
		return proxyUser;
	}

	public void setProxyUser(String proxyUser) {
		if (proxyUser != null && proxyUser.trim().isEmpty() == false) {
			this.proxyUser = proxyUser.trim();
		} else {
			this.proxyUser = null;
		}
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		if (proxyPassword != null && proxyPassword.trim().isEmpty() == false) {
			this.proxyPassword = proxyPassword.trim();
		} else {
			this.proxyPassword = null;
		}
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

	public long getMainWaitInterval() {
		return mainWaitInterval;
	}

	public void setMainWaitInterval(long mainWaitInterval) {
		this.mainWaitInterval = mainWaitInterval;
	}

	public long getInnerLoopWaitInterval() {
		return innerLoopWaitInterval;
	}

	public void setInnerLoopWaitInterval(long innerLoopWaitInterval) {
		this.innerLoopWaitInterval = innerLoopWaitInterval;
	}
	
	public void collectColumns() throws Exception {
		Columns columns = analyticsClient.metadata().columns().list("ga").execute();
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
		listUnsampledReports = new ArrayList<UnsampledReport>();
		for (Profile profile : listProfiles) {
			Thread.sleep(innerLoopWaitInterval);
			UnsampledReports reports = analyticsClient.management().unsampledReports().list(
				      profile.getAccountId(),
				      profile.getWebPropertyId(),
				      profile.getId()).execute();
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

}