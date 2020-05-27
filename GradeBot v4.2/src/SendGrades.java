import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.Stack;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlInlineFrame;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;


public class SendGrades {

	final static String username = "graderCheck@gmail.com";
    final static String password = "*****";
    public static final String ACCOUNT_SID="*****";
	public static final String AUTH_TOKEN = "*****";
    
    public static TreeMap<String, People> map;
   
	public static final boolean DEBUG=true;
	
	public static void main(String[] args) throws FailingHttpStatusCodeException, MalformedURLException, IOException, InterruptedException {															
		boolean initialRun=true;		
		java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);  //Used for initial run, setting base to first run.
		 //removes annoying HTMLUnit errors.
		//VERSION 4.2
		map=new TreeMap<String, People>();												//Instantiates Map with People
		int time;			
		HtmlPage currentPage;
		Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
		while(true) {		//Beginning of loop
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	        Date date = new Date();
			System.out.println("Loop v4.0: "+dateFormat.format(date)+" "+java.time.LocalTime.now());
			int sleep=60000;
			time= Integer.parseInt((java.time.LocalTime.now()+"").substring(0,2));		//Time check for later//Basing off time of day, puts the loop to sleep
			if(time>=0&&time<6) {
				sleep=10800000;
			}
			else {
				sleep=5000*60;
			}
			try {
			Scanner file=new Scanner(new File("gradesLedger.dat"));						//Scanner
			while(file.hasNext()) {			
				String key=file.next();	
				String val=file.next();
				String num=file.next();
				if(!map.containsKey(key)) map.put(key, new People(val,num));		//Rechecks file every run to see if there is any new people, adds them to list if necessary
			}
			file.close();
			for(String key:map.keySet()) {
				boolean b=true;															//boolean for if s!=base
				map.get(key).resetClient();												//resets client because it's closed at the end. Necessary for this to work
				currentPage = map.get(key).getClient().getPage("https://home-access.cfisd.net/HomeAccess/Account/LogOn?ReturnUrl=%2fhomeaccess%2f"); 
				HtmlInput queryInput = currentPage.getElementByName("LogOnDetails.UserName"); 
				queryInput.setValueAttribute(key); 										//All of this just gets the grades page
				HtmlInput queryInput2 = currentPage.getElementByName("LogOnDetails.Password");
				queryInput2.setValueAttribute(map.get(key).getPass()); 
				HtmlButton submitBtn = currentPage.getFirstByXPath("//button[@role='button']");
				currentPage = submitBtn.click();
				String s=currentPage.asText();											//Instantiates s as current grades
				s=s.replaceAll("\\s+", " ").trim();
				if(initialRun) {
					map.get(key).setBasis(s);								//Sets base to s on the first run only
					HtmlElement htmlElement = currentPage.getFirstByXPath("//div[@id='hac-Classes']"); 
					HtmlPage gradePage=htmlElement.click();
					HtmlInlineFrame iframe = (HtmlInlineFrame) gradePage.getElementsByTagName("iframe").get(0);
					HtmlPage innerPage = (HtmlPage) iframe.getEnclosedPage();
					String g=innerPage.asXml();
					g=g.replaceAll("\\s+", " ").trim();
					map.get(key).setGradeStringPage(g);
				}
//				System.out.println(map.get(key).getGradeStringPage());
				System.out.println(s);
				if(!initialRun&&!s.equals(map.get(key).getBasis())) {
					if(!s.equals("HomeAccessCenterLoginVer4xHomeAccessCenterallowsparentsandstudentstoviewstudentregistration,scheduling,attendance,classworkassignmentsandgradeinformation._________________________________ClickHeretowatchanimportantvideoaboutHACChangesPleaseenteryouraccountinformationforHomeAccessCenter.UserName:"+key+"Password:"+map.get(key).getPass()+"LoginForgotMyUsernameorPassword�1990-2018SunGardPublicSectorInc.AllRightsReserved.ThisprogramisPROPRIETARYandCONFIDENTIALinformationofSunGardPublicSectorInc.,andmaynotbedisclosedorusedexceptasexpresslyauthorizedinalicenseagreementcontrollingsuchuseanddisclosure.Unauthorizeduseofthisprogramwillresultinlegalproceedings,civildamagesandpossiblecriminalprosecution.")) {
						b=false;
						//System.out.println(map.get(key).getGradeStringPage());
						HtmlElement htmlElement = currentPage.getFirstByXPath("//div[@id='hac-Classes']"); 
						HtmlPage gradePage=htmlElement.click();
						HtmlInlineFrame iframe = (HtmlInlineFrame) gradePage.getElementsByTagName("iframe").get(0);
						HtmlPage innerPage = (HtmlPage) iframe.getEnclosedPage();
						String g=innerPage.asXml();
						g=g.replaceAll("\\s+", " ").trim();
						String x=map.get(key).getGradeStringPage();
						//System.out.println(g);
						addGrades(x,g,key);
						if(true) {
							PrintWriter printer=new PrintWriter(new FileWriter(new File("logger.txt"),true),true);
							printer.println("ID: "+key);
							printer.println("Original: "+map.get(key).getGradeStringPage());
							printer.println("New:      "+g);
							printer.close();
						}
						map.get(key).setGradeStringPage(g);
						map.get(key).setBasis(s);
						
					}
					map.get(key).getClient().close();										//Closes client, allowing it to be reopened on successive runs to scan again
				}
				if(!map.get(key).getStack().empty()) {
					sendEmail(map.get(key).getPass(),key);		//If s!=base, then send out the email text
				}
			}
			initialRun=false;
			Thread.sleep(sleep);														//Sleeps for certain amount of time based off time of day
			}
			catch(Exception e) {
				System.out.println("Error : " + e);
				//throw e;
			}
		}
	}
	
	
	public static void sendEmail(String Number, String key) throws IOException {	//All of this is just the set up for the email
		String s="";
		while(map.get(key).getStack().size()>0) {
			Grade gr=map.get(key).getStack().pop();
			s+=gr+"\n";
		}
		if(true) {
			PrintWriter printer=new PrintWriter(new FileWriter(new File("logger.txt"),true),true);
			printer.println("ID: "+key+"\n"+s+"\n");
			printer.close();
		}
		String number=map.get(key).getNum();
		Message message = Message.creator(new PhoneNumber(number),new PhoneNumber("+12015146455"),s).create();

		
	}
	public static void addGrades(String s, String x,String key) {
		
//		System.out.println("!!!!!"+s);
		String[] oriString=s.split("<tr class=\"sg-asp-table-data-row\"> <td> \\d{2}/\\d{2}/\\d{4}");
		String[] newString=x.split("<tr class=\"sg-asp-table-data-row\"> <td> \\d{2}/\\d{2}/\\d{4}");
		if(oriString.length>newString.length) return;
		int index =1;
//		for(int i=1;i<oriString.length&&i<newString.length;i++) {
//			oriString[i]=oriString[i].replaceAll("\\s+", " ").trim();
//			newString[i]=newString[i].replaceAll("\\s+", " ").trim();
//			System.out.println("OriString "+"i= "+i+" "+oriString[i]);
//			System.out.println("NewString "+"i= "+i+" "+newString[i]);
//		}
//		for(int i=oriString.length;i<newString.length;i++) {
//			newString[i]=newString[i].replaceAll("\\s+", " ").trim();
//			System.out.println("NewString "+"i= "+i+" "+newString[i]);
//		}
		for(int i=1;i<oriString.length&&index<newString.length;i++) {
			int newIndex=index;
			index++;
			String newS=newString[newIndex].trim();
			String oldS=oriString[i].trim();
			if(oldS.length()>700&&newS.length()>700) {
				oldS=oldS.substring(0, 700);
				newS=newS.substring(0, 700);
			}
			if(!oldS.equals(newS)) {
				String nameOld=getName(oriString[i]).trim();
				String nameNew=getName(newString[newIndex]).trim();
				double gradeNew=getGrade(newString[newIndex]);
				//System.out.println("Old Name "+nameOld+"\nNew Name "+nameNew+" "+oldS.equals(newS));
				//System.out.println("\n\n"+oldS+"\n"+newS+"\n\n");
				
				if(!nameOld.equals(nameNew)) {
					i--;
					if(gradeNew<0) {
						
						continue;
					}
					map.get(key).getStack().add(new Grade(nameNew,gradeNew+""));
				}
				else {
					if(gradeNew<0) continue;
					map.get(key).getStack().add(new Grade(nameNew,gradeNew+""));
				}
				
			}
		}
	}

	public static String getName(String s) {
		String name="";
		name=s.substring(s.indexOf("Classwork:")+11,s.indexOf("Category"));
		return name;
	}
	public static double getGrade(String s) {
		String grade="";
//		System.out.println("GetGrade Test "+ s);
		Pattern p=Pattern.compile("(</td>\\s*<td>\\s*Checking for Understanding\\s*</td>\\s*<td>.{0,8}</td>)|(</td>\\s*<td>\\s*Relevant Applications\\s*</td>\\s*<td>.{0,8}</td>)|(</td>\\s*<td>\\s*Summative Assessments\\s*</td>\\s*<td>.{0,8}</td>)");
		Matcher m=p.matcher(s);
		m.find();
		grade=m.group(0);
		if(grade.contains("<\\\td><td>"))grade=grade.substring(grade.indexOf("</td><td>",10)+10,grade.lastIndexOf("</td>"));
		else grade=grade.substring(grade.indexOf("</td> <td>",10)+10,grade.lastIndexOf("</td>"));
		double d=-1;
		//System.out.println("Grade: "+grade);
		if(grade.trim().equalsIgnoreCase("z")) d=0;
		else if(!grade.trim().equals("")&&!grade.trim().equalsIgnoreCase("x")) {
			d=Double.parseDouble(grade);
		}
		
		return d;
		
	}
	
}

class People{
	private String password;
	private String number;
	private String basis;
	private String gradeStringPage;
	private WebClient client;
	private WebClient gradePage;
	private Stack<Grade> stack;
	
	public People(String p, String num) {
		password=p;
		number=num;
		basis="";
		gradeStringPage="";
		client=new WebClient();
		gradePage=new WebClient();
		stack=new Stack<Grade>();
	}
	public String getPass() {
		return password;
	}
	public String getNum() {
		return number;
	}
	public String getBasis() {
		return basis;
	}
	public String getGradeStringPage() {
		return gradeStringPage;
	}
	public WebClient getClient() {
		return client;
	}
	public WebClient getGradePage() {
		return gradePage;
	}
	public Stack<Grade> getStack(){
		return stack;
	}
	public void setBasis(String b) {
		basis=b;
	}
	public void setGradeStringPage(String g) {
		gradeStringPage=g;
	}
	public void resetClient() {
		client=new WebClient();
	}
}

class Grade{
	//public String className;
	public String assignName;
	public double assignGrade;
	public Grade(String a,String g) {
		//className=c;
		assignName=a;
		assignGrade=Double.parseDouble(g.trim());
	}
//	public String getClassName() {
//		return className;
//	}
	public String toString() {
		String s="";
		DecimalFormat d=new DecimalFormat("00.00");
		s+=assignName+" : "+d.format(assignGrade);
		return s;
	}
}