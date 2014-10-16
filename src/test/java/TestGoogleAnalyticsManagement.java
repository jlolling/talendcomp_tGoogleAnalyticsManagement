import java.util.Date;

import com.google.api.services.analytics.model.Account;
import com.google.api.services.analytics.model.Profile;
import com.google.api.services.analytics.model.Segment;
import com.google.api.services.analytics.model.Webproperty;

import de.jlo.talendcomp.googleanalytics.GoogleAnalyticsManagement;

public class TestGoogleAnalyticsManagement {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		testGAManagement();
	}

	public static void testGAManagement() {

		GoogleAnalyticsManagement gm = new GoogleAnalyticsManagement();
		gm.setApplicationName("GATalendComp");

//		gm.setAccountEmail("503880615382@developer.gserviceaccount.com");
//		gm.setKeyFile("/var/testdata/ga/config/2bc309bb904201fcc6a443ff50a3d8aca9c0a12c-privatekey.p12");

		gm.setAccountEmail("422451649636@developer.gserviceaccount.com");
		gm.setKeyFile("/var/testdata/ga/config/af21f07c84b14af09c18837c5a385f8252cc9439-privatekey.p12");
		gm.setTimeOffsetMillisToPast(10000);
		gm.setTimeoutInSeconds(240);
		gm.reset();
		try {
			System.out.println("initialize client....");
			gm.initializeAnalyticsClient();
//			System.out.println("collect accounts....");
//			gm.collectAccounts();
//			System.out.println("collect webproperties....");
//			gm.collectWebProperties();
			System.out.println("collect profiles....");
			gm.collectProfiles();
//			System.out.println("collect segments....");
//			gm.collectSegments();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
//		System.out.println("count segments:" + gm.getListSegments().size());
		while (gm.next()) {
			System.out.println("-----------------------------------------------");
			System.out.println(gm.getCurrentIndex());
			if (gm.hasCurrentAccount()) {
				Account account = gm.getCurrentAccount();
				System.out.println("account id:" + Long.parseLong(account.getId()));
				System.out.println("account:" + account.getName());
			}
			if (gm.hasCurrentWebproperty()) {
				Webproperty webproperty = gm.getCurrentWebproperty();
				System.out.println("webproperty url:" + webproperty.getWebsiteUrl());
			}
			if (gm.hasCurrentProfile()) {
				Profile profile = gm.getCurrentProfile();
				System.out.println("profile id:" + Long.parseLong(profile.getId()));
				System.out.println("profile:" + new Date(profile.getCreated().getValue()));
			}
			if (gm.hasCurrentSegment()) {
				Segment segment = gm.getCurrentSegment();
				segment.getId();
				System.out.println("segment id:" + segment.getSegmentId());
				System.out.println("segment name:" + segment.getName());
				System.out.println("segment def:" + segment.getDefinition());
				System.out.println("segment kind:" + segment.getKind());
			}
		}
		// System.out.println("############################# " + i +
		// " ########################");

	}

	
}
