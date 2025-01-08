/*=====================================================*/
/*                                                     */
/* Copyright (c) University of Warwick 2005            */
/*                                                     */
/* Author T.R.Marsh                                    */
/*=====================================================*/

// WARNING: only edit the file Udriver.java.template
// Udriver.java is automatically generated from the template file

package warwick.marsh.ultracam.udriver;

import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.JOptionPane;
import java.lang.Integer;
import java.util.StringTokenizer;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import java.awt.*;
import java.awt.event.*;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.Socket;
import java.net.ServerSocket;

import java.text.DecimalFormat;

import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import warwick.marsh.util.*;
import warwick.marsh.ultracam.LogPanel;
import warwick.marsh.ultracam.ReplyPanel;
import warwick.marsh.ultracam.Telescope;
import warwick.marsh.ultracam.Telescope;

/** UDriver is a program to edit applications for ULTRACAM and to drive it
 * The program has the following functionality:
 * <ul>
 * <li> Application definition (drift, 1 window pair etc) through a GUI
 * <li> Set binning factors and readout speed
 * <li> Set window positions and sizes
 * <li> Dump the application & window definition to local XML files that can
 * be later re-loaded to drive ULTRACAM runs
 * <li> Act as a server for 'rtplot' so that the latter can grab the current
 * set of windows.
 * <li> Post XML applications to the ultracam camera and data servers
 * </ul>
 *
 * Various aspects of the program can be defined using the configuration file which can be set when
 * running Udriver as a system property CONFIG_FILE. For example you can add -DCONFIG_FILE=my_setup_file
 * If you don't it defaults to udriver.conf in whichever directory you run the program in.
 * 
 * @author  Tom Marsh
 *
 */

public class Udriver extends JFrame {

    // Telescope data. See the class for a full description of the fields
    private static final Telescope[] TELESCOPE_DATA = {
	new Telescope("VLT", new double[] {26.54, 28.35, 27.69, 27.55, 26.71}, 0.15, "vlt.xml"),
	new Telescope("WHT", new double[] {25.11, 26.92, 26.26, 26.12, 25.28}, 0.30, "wht.xml"),
	new Telescope("NTT", new double[] {24.47, 26.28, 25.92, 25.48, 24.64}, 0.35, "ntt.xml"),
    };

    // Generic application to initialise servers
    private static final String   GENERIC_APP  = "ultracam.xml";

    // The following is used to pass the telescope data around
    private Telescope _telescope = null;

    //------------------------------------------------------------------------------------------

    // Sky parameters

    // Extinction, mags per unit airmass
    private static final double[] EXTINCTION = {0.50, 0.19, 0.09, 0.05, 0.04};

    // Sky brightness, mags/arcsec**2, dark, grey, bright, in ugriz
    private static final double[][] SKY_BRIGHT = {
	{22.4, 22.2, 21.4, 20.7, 20.3},
	{21.4, 21.2, 20.4, 20.1, 19.9},
	{18.4, 18.2, 17.4, 17.9, 18.3}
    };

    // Instrument parameters

    // Readout parameters
    private static final double   GAIN_TURBO = 1.5;           // electrons per count
    private static final double   GAIN_FAST  = 1.4;           // electrons per count
    private static final double   GAIN_SLOW  = 1.3;           // electrons per count

    // Readout noise for 1x1, 2x2, 4x4, 8x8
    // EDIT: READ NOISE FOR TURBO VERY APPROX
    private static final double[] READ_NOISE_TURBO = {7.0, 7.0, 7.0, 7.0};
    private static final double[] READ_NOISE_FAST  = {4.9, 4.9, 5.1, 6.4};
    private static final double[] READ_NOISE_SLOW  = {3.6, 3.6, 4.0, 5.4};

    // Dark count rate, counts/sec/pixel
    private static final double DARK_COUNT = 0.1;

    // Timing parameters from Vik
    public static final double INVERSION_DELAY = 110.;   // microseconds
    public static final double VCLOCK_FRAME    = 23.3;   // microseconds
    public static final double VCLOCK_STORAGE  = 23.3;   // microseconds
    public static final double HCLOCK          = 0.48;   // microseconds
    //EDIT
    public static final double CDS_TIME_FDD    = 1.84;    // microseconds
    public static final double CDS_TIME_FBB    = 4.40;    // microseconds
    public static final double CDS_TIME_CDD    = 9.76;    // microseconds
    public static final double SWITCH_TIME     = 0.56;    // microseconds

    // Special values of NY when pipe shift hits a minimum
    public static final int[] specialNy = {8, 10, 13, 18, 21, 24, 31, 38, 41, 49, 54, 60, 68, 79, 93, 114, 147, 206, 344};
  
    // Colours
    public static final Color DEFAULT_COLOUR    = new Color(220, 220, 255);
    public static final Color SEPARATOR_BACK    = new Color(100, 100, 100);
    public static final Color SEPARATOR_FORE    = new Color(150, 150, 200);
    public static final Color LOG_COLOUR        = new Color(240, 230, 255);
    public static final Color ERROR_COLOUR      = new Color(255, 0,   0  );
    public static final Color WARNING_COLOUR    = new Color(255, 100, 0  );
    public static final Color GO_COLOUR         = new Color(0,   255, 0  );
    public static final Color STOP_COLOUR       = new Color(255, 0,   0  );

    // Font
    public static final Font DEFAULT_FONT = new Font("Serif", Font.BOLD, 12);

    // Width for horizontal separator
    public static final int SEPARATOR_WIDTH = 5;

    // Recognised by the method 'speed'
    public static final int DETAILED_TIMING = 1;
    public static final int TIMING_UPDATE   = 2;
    public static final int CYCLE_TIME_ONLY = 3;
    private boolean _validStatus = true; 
    private boolean _magInfo     = true;


    // Exposure timer, active run timer, disk space display, checkRun
    // ActionListener that checks for run numbers
    private Timer      _exposureMeter = null;
    private Timer      _runActive     = null;
    private JTextField _exposureTime  = new JTextField("0", 7);
    private JTextField _spaceUsed     = new JTextField("0", 7);
    private JTextField _runNumber     = new JTextField("", 7);
    private ActionListener _checkRun  = null;
    
    // Thresholds for changing colour of disk space 
    public static final int DISK_SPACE_WARN   = 1500;
    public static final int DISK_SPACE_DANGER = 1800;
    
    // These are used to store values from last posted application
    private int _nbytesPerImage  = 0;
    private int _nexposures      = 1;
    private double _timePerImage = 0;

    private int _filterIndex    = 1;
    private int _skyBrightIndex = 1;

	private String _runType = "data";
	private boolean _acquisitionState = false;

    // Configuration file
    public static final String CONFIG_FILE = System.getProperty("CONFIG_FILE", "udriver.conf");

    // Configurable values
    public static boolean RTPLOT_SERVER_ON;
    public static boolean ULTRACAM_SERVERS_ON;
    public static boolean OBSERVING_MODE;
    public static boolean DEBUG;
    public static boolean FILE_LOGGING_ON;
    public static String  TELESCOPE             = null;
    public static String  HTTP_CAMERA_SERVER    = null;
    public static String  HTTP_DATA_SERVER      = null;
    public static String  HTTP_PATH_GET         = null;
    public static String  HTTP_PATH_EXEC        = null;
    public static String  HTTP_PATH_CONFIG      = null;
    public static String  HTTP_SEARCH_ATTR_NAME = null;

    public static String  APP_DIRECTORY         = null;
    public static boolean XML_TREE_VIEW;
    public static boolean TEMPLATE_FROM_SERVER;
    public static String  TEMPLATE_DIRECTORY    = null;
    public static boolean EXPERT_MODE;
    public static String  LOG_FILE_DIRECTORY    = null;
    public static boolean CONFIRM_ON_CHANGE;
    public static boolean DATA_FROM_IMEDIA1     = true;
    public static boolean CHECK_FOR_MASK;
	public static boolean USE_UAC_DB			= true;
	public static int SERVER_READBACK_VERSION	= 0;
	public static String  UAC_DATABASE_HOST;

    public static String   WINDOW_NAME          = new String("window pair");
    public static String[] TEMPLATE_LABEL       = null;
    public static String[] TEMPLATE_PAIR        = null;
    public static String[] TEMPLATE_APP         = null;
    public static String[] TEMPLATE_ID          = null;
    public static String   POWER_ON             = null;
    public static String   POWER_OFF            = null;

    // Binning factors
    private int xbin    = 1;
    private int ybin    = 1;
    private IntegerTextField xbinText = new IntegerTextField(xbin, 1, 8, 1, "X bin factor", true, DEFAULT_COLOUR, ERROR_COLOUR, 2);
    private IntegerTextField ybinText = new IntegerTextField(ybin, 1, 8, 1, "Y bin factor", true, DEFAULT_COLOUR, ERROR_COLOUR, 2);

    // NBLUE u'-band coadd factor
    private int nblue = 1;
    private IntegerTextField nblueText = new IntegerTextField(nblue, 1, 1000, 1, "u-band cycle factor", true, DEFAULT_COLOUR, ERROR_COLOUR, 4);

    private JComboBox templateChoice;
    private int numEnable;
    
    private String applicationTemplate    = new String("Fullframe + clear");
    private String oldApplicationTemplate = new String("Fullframe + clear");

    // Readout speeds
    private static final String[] SPEED_LABELS = {
	"Turbo",
	"Fast",
	"Slow"
    };
    private JComboBox speedChoice;
    private String readSpeed = "Slow";

    private static final String SLOW_SPEED  = "0xcdd";
    private static final String FAST_SPEED  = "0xfbb";
    private static final String TURBO_SPEED = "0xfdd";

	// Filters
	private static final String[] BLUE_FILTER_NAMES = {
		"Super u'","u'","NBF3500","Clear","Lab","Special","(None)"
	};
	private static final String[] GREEN_FILTER_NAMES = {
		"Super g'", "g'","HeII","BCont","NBF4170","Clear","Lab","Special","(None)"
	};
	private static final String[] RED_FILTER_NAMES = {
	    "Super r'","Super i'", "Super z'", "r'","i'","z'","RCont","NaI","HA-N","HA-B",
		"Clear","Lab","Special","(None)"
	};
	private JComboBox _filter1;
	private JComboBox _filter2;
	private JComboBox _filter3;
	private String defaultFilter1 = "Super u'";
	private String defaultFilter2 = "Super g'";
	private String defaultFilter3 = "Super r'";

    // Exposure delay measured in 0.1 millisecond intervals, so prompted
    // for in terms of millseconds plus a small text field of 0.1 milliseconds
    // that is only enabled in expert mode as it comes with some dangers.
    private int expose = 5;
    private IntegerTextField exposeText     = new IntegerTextField(0, 0, 100000, 1, "exposure, milliseconds", true, DEFAULT_COLOUR, ERROR_COLOUR, 6);
    private IntegerTextField tinyExposeText = new IntegerTextField(5, 0, 9, 1, "exposure increment, 0.1 milliseconds", true, DEFAULT_COLOUR, ERROR_COLOUR, 2);
    private int numExpose = -1;
    private IntegerTextField numExposeText = new IntegerTextField(numExpose, -1, 100000, 1, "Number of exposures", true, DEFAULT_COLOUR, ERROR_COLOUR, 6);

    private static JLabel windowsLabel = new JLabel("Windows");
    private static JLabel ystartLabel  = new JLabel("ystart");
    private static JLabel xstartLabel  = new JLabel("xstart");
    private static JLabel xleftLabel   = new JLabel("xleft");
    private static JLabel xrightLabel  = new JLabel("xright");
    private static JLabel nxLabel      = new JLabel("nx");
    private static JLabel nyLabel      = new JLabel("ny");

    // Fields for user information
    private static JTextField _objectText     = new JTextField("", 15);
    //private static JTextField _filterText     = new JTextField("", 15);
    private static JTextField _progidText     = new JTextField("", 15);
    private static JTextField _piText         = new JTextField("", 15);
    private static JTextField _observerText   = new JTextField("", 15);

	private static JRadioButton _dataButton = new JRadioButton("data");
	private static JRadioButton _acqButton = new JRadioButton("acquire");
	private static JRadioButton _biasButton = new JRadioButton("bias");
	private static JRadioButton _flatButton = new JRadioButton("flat");
	private static JRadioButton _darkButton = new JRadioButton("dark");
	private static JRadioButton _techButton = new JRadioButton("tech");

    // Fields for signal-to-noise estimates
    private static DoubleTextField _magnitudeText  = new DoubleTextField(18.0, 5.,  35., 0.1,  "Target magnitude",    true, DEFAULT_COLOUR, ERROR_COLOUR, 6);
    private static DoubleTextField _seeingText     = new DoubleTextField( 1.0, 0.2, 20., 0.1,  "Seeing, FWHM arcsec", true, DEFAULT_COLOUR, ERROR_COLOUR, 6);
    private static DoubleTextField _airmassText    = new DoubleTextField(1.5, 1.0, 5.0,  0.05, "Airmass", true, DEFAULT_COLOUR, ERROR_COLOUR, 6);

    // ULTRACAM windows come in pairs
    private static WindowPairs  _windowPairs;

    private static JFileChooser _rtplotFileChooser;
    private static JFileChooser _xmlFileChooser;
    private static File        _rtplotFile = null;
    private static File        _xmlFile    = null;
    private static ReplyPanel  _replyPanel = null;
    public  static LogPanel logPanel   = null;

    private DocumentBuilder _documentBuilder;
    private Transformer     _transformer;
    
    // Use this a fair bit, so just make one
    private static GridBagLayout gbLayout = new GridBagLayout();

    // To switch between setup & observing panels
    JTabbedPane _actionPanel = null;
    JPanel _expertSetupPanel = null;
    JPanel _noddySetupPanel  = null;

    // Action buttons associated with ULTRACAM servers
    private static JButton loadApp          = new JButton("Load application");
    private static JButton enableChanges    = new JButton("Unfreeze Udriver");
    private static JButton syncWindows      = new JButton("Sync windows");
    private static JButton postApp          = new JButton("Post application");
    private static JButton startRun         = new JButton("Start exposure");
    private static JButton stopRun          = new JButton("Stop exposure");
    private static JButton setupAll         = new JButton("Initialise");
    private static JButton resetSDSUhard    = new JButton("Reset SDSU hardware");
	private static JButton resetSDSUsoft	= new JButton("Reset SDSU software");
    private static JButton resetPCI         = new JButton("Reset PCI board");
	private static JButton resetAll			= new JButton("System reset");
    private static JButton setupServer      = new JButton("Setup the servers");
    private static JButton powerOn          = new JButton("Power on");
    private static JButton noddyPowerOff    = new JButton("Power off");
    private static JButton expertPowerOff   = new JButton("Power off");
	// for a custom server command in expert mode
    private static JTextField _commandText   = new JTextField("", 15);
	private static JButton execExpertCmd	= new JButton("EXEC");

    // Maintain a record of whether buttons are or are not enabled independent
    // of whether they actually are to allow switching between expert and
    // non-expert modes
    private boolean postApp_enabled         = false;
    private boolean startRun_enabled        = false;
    private boolean stopRun_enabled         = false;
    private boolean resetSDSU_enabled       = true;
    private boolean resetPCI_enabled        = false;
    private boolean setupServer_enabled     = false;
    private boolean powerOn_enabled         = false;
    private boolean powerOff_enabled        = false;
	private boolean expertCmd_enabled		= false;

    // Timing info fields
    private JTextField _frameRate        = new JTextField("", 7);
    private JTextField _cycleTime        = new JTextField("", 7);
    private JTextField _dutyCycle        = new JTextField("", 7);
    private JTextField _totalCounts      = new JTextField("", 7);
    private JTextField _peakCounts       = new JTextField("", 7);
    private JTextField _signalToNoise    = new JTextField("", 7);
    private JTextField _signalToNoiseOne = new JTextField("", 7);

    // Settings menu items
    private JCheckBoxMenuItem _setExpert;
    private JCheckBoxMenuItem _templatesFromServer;
    private JCheckBoxMenuItem _ucamServersOn;
    private JCheckBoxMenuItem _fileLogging;
    private JCheckBoxMenuItem _responseAsText;
    private JCheckBoxMenuItem _confirmOnChange;
    private JCheckBoxMenuItem _dataFromImedia1;
    private JCheckBoxMenuItem _checkForMask;
    private JCheckBoxMenuItem _enforceSave;
	private JCheckBoxMenuItem _useUACdb;

    // Member class to check for changes in format
    private CheckFormat _format;

    // Check for change of settings.
    private Settings  _dataFormat;

    // The flag says that the current setup has been been used for a run but not saved
    private static boolean _unsavedSettings = false;

    // Name of target used in the application last posted to the servers
    private String _postedTarget = "UNDEFINED";

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** This is the constructor which does the hard work of setting up the GUI, define
     * the actions panel, menu bar, timing panel, windows pansl etc. The individual
     * components are hived off into one-off methods to make it easier to see what does
     * what.
     */
    public Udriver () {

	try {

	    // Set the colours & fonts

	    UIManager.put("OptionPane.background",         DEFAULT_COLOUR);
	    UIManager.put("Panel.background",              DEFAULT_COLOUR);
	    UIManager.put("Button.background",             DEFAULT_COLOUR);
	    UIManager.put("CheckBoxMenuItem.background",   DEFAULT_COLOUR);
	    UIManager.put("SplitPane.background",          DEFAULT_COLOUR);
	    UIManager.put("Table.background",              DEFAULT_COLOUR);
	    UIManager.put("Menu.background",               DEFAULT_COLOUR);
	    UIManager.put("MenuItem.background",           DEFAULT_COLOUR);
	    UIManager.put("TextField.background",          DEFAULT_COLOUR);
	    UIManager.put("ComboBox.background",           DEFAULT_COLOUR);
	    UIManager.put("TabbedPane.background",         DEFAULT_COLOUR);
	    UIManager.put("TabbedPane.selected",           DEFAULT_COLOUR);
	    UIManager.put("MenuBar.background",            DEFAULT_COLOUR);
	    UIManager.put("window.background",             DEFAULT_COLOUR);  
	    UIManager.put("TextPane.background",           LOG_COLOUR);
	    UIManager.put("Tree.background",               LOG_COLOUR);
	    UIManager.put("RadioButtonMenuItem.background",DEFAULT_COLOUR);
	    UIManager.put("RadioButton.background",        DEFAULT_COLOUR);

	    UIManager.put("Table.font",                    DEFAULT_FONT);
	    UIManager.put("TabbedPane.font",               DEFAULT_FONT);
	    UIManager.put("OptionPane.font",               DEFAULT_FONT);
	    UIManager.put("Menu.font",                     DEFAULT_FONT);
	    UIManager.put("MenuItem.font",                 DEFAULT_FONT);
	    UIManager.put("Button.font",                   DEFAULT_FONT);
	    UIManager.put("ComboBox.font",                 DEFAULT_FONT);
	    UIManager.put("RadioButtonMenuItem.font",      DEFAULT_FONT);
	    UIManager.put("RadioButton.font",              DEFAULT_FONT);

	    // Load configuration file
	    loadConfig();

	    //-----------------------------------------------------------------------------------------------------
	    // Information panels setup
	    _replyPanel = new ReplyPanel();
	    logPanel    = new LogPanel(LOG_FILE_DIRECTORY);
	    if(FILE_LOGGING_ON)
		logPanel.startLog();

	    //-----------------------------------------------------------------------------------------------------
	    // Define the rtplot and XML file choosers

	    _rtplotFileChooser = new JFileChooser();
	    _rtplotFileChooser.setFileFilter(new FileFilterDat());

	    _xmlFileChooser    = new JFileChooser();
	    _xmlFileChooser.setFileFilter(new FileFilterXML());
	    _xmlFileChooser.setCurrentDirectory(new File(APP_DIRECTORY));

	    //-----------------------------------------------------------------------------------------------------
	    // Create an XML document builder & transformer
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    dbf.setValidating(false);
	    _documentBuilder = dbf.newDocumentBuilder();
	    
	    TransformerFactory factory = TransformerFactory.newInstance();
	    _transformer = factory.newTransformer();


	    //-----------------------------------------------------------------------------------------------------
	    // Enable the window destruct signal
	    this.addWindowListener(
				   new WindowAdapter() {

				       public void windowClosing(WindowEvent e){
					   if(logPanel.loggingEnabled())
					       logPanel.stopLog();
					   try {
						   String path = System.getProperty("user.home");
						   Document document = _createXML(false);
						   FileWriter fwriter = new FileWriter(path + "/.udriver.xml");
						   _transformer.transform(new DOMSource(document), new StreamResult(fwriter));
					   } catch (Exception ex) {System.out.println(ex);}
					   System.exit(0);
				       }

				       public void windowOpened(WindowEvent e){
					   try {
					       Class.forName("com.mysql.jdbc.Driver").newInstance();
					   } catch (Exception ie) {
					       USE_UAC_DB = false;
					       _useUACdb.setState(false);
					       _useUACdb.setEnabled(false);
					       System.out.println("Couldn't import jdbc, turning UAC db lookup off.");
					   }
					   String path = System.getProperty("user.home");
					   try{
					       _xmlFile = new File(path + "/.udriver.xml");
					       if(_xmlFile.exists())
						   _loadApp(true);
					   } catch (Exception ex) {System.out.println(ex);}
				       }
				   }
				);	

	    //-----------------------------------------------------------------------------------------------------
	    // Set up basic frame
	    // If you change the next string, you must change Makefile as well where
	    // a sed operation changes the version number.
	    this.setTitle("ULTRACAM window creator and driver, version 4");
	    this.setSize( 800, 400);
	    
	    // The basic layout is to have action buttons on the top-left, parameter controls on the top-right, 
	    // timing information panels on the middle-left, and target info in the middle-right 
	    // Finally information back from the servers etc goes along the bottom. All these panels
	    // have their own methods or classes to avoid this constructor from becoming excessively long.
	    // GridBagLayout manager is used to arrange the panels.

	    Container container = this.getContentPane();
	    container.setBackground(DEFAULT_COLOUR);
	    container.setLayout(gbLayout);
	    
	    // Menu bar
	    JMenuBar menubar = new JMenuBar();
	    this.setJMenuBar(menubar);
	    
	    // File menu
	    menubar.add(createFileMenu());

	    // Settings menu
	    menubar.add(createSettingsMenu());

	    // Now the main panels of the GUI. 

	    // Action panel in top-left
	    addComponent( container, createActionsPanel(),  0, 0,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);

	    // Middle-left panel for displaying target and s-to-n information
	    addComponent( container, createTimingPanel(),  0, 2,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);

	    // When observing we have XML information and logger panels
	    if(OBSERVING_MODE){
		
		JSplitPane infoPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logPanel, _replyPanel);
		infoPanel.setBorder(new EmptyBorder(15,15,15,15));
		
		addComponent( container, infoPanel, 0, 4,  3, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	    }

	    // Some horizontal space between the left- and right-hand panels
	    addComponent( container, Box.createHorizontalStrut(30), 1, 0,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    addComponent( container, Box.createHorizontalStrut(30), 1, 2,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	    // Top-right panel defines the parameters
	    addComponent( container, createWindowPanel(), 2, 0,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	    // Middle-right panel defines the target info
	    addComponent( container, createTargetPanel(),   2, 2,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	    // Horizontal separator across whole GUI to separate essential (above) from nice-to-have (below)
	    JSeparator hsep = new JSeparator();
	    hsep.setBackground(SEPARATOR_BACK);
	    hsep.setForeground(SEPARATOR_FORE);
	    addComponent( container, hsep, 0, 1,  3, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	    Dimension dim = container.getPreferredSize();
	    hsep.setPreferredSize(new Dimension(dim.width, SEPARATOR_WIDTH));

	    // Ensure correct configuration of enabled buttons
	    _setEnabledActions();

	    // Update the colours while ensuring that paste operations remian disabled in numeric fields
	    updateGUI();

	    // Make the whole GUI visible
	    pack();
	    setVisible(true);

	    // Define timer to provide regular updating of timing information
	    // and to check whether windows are synchronised
	    // Task to perform
	    ActionListener taskPerformer = new ActionListener() {
		    public void actionPerformed(ActionEvent event) {
			speed(TIMING_UPDATE);
			if(_areSynchronised()){
			    syncWindows.setEnabled(false);
			    syncWindows.setBackground(DEFAULT_COLOUR);
			}else{
			    syncWindows.setEnabled(true);
			    syncWindows.setBackground(WARNING_COLOUR);
			}
		    }
		};	
	
	    // Checks once per second
	    Timer tinfoTimer = new Timer(1000, taskPerformer);	
	    tinfoTimer.start();

	    // Store current format in order to check for changes.
	    _format     = new CheckFormat();
	    _dataFormat = new Settings();

	}
	catch(Exception e){
	    e.printStackTrace();
	    System.out.println(e);
	    System.out.println("Udriver exiting.");
	    System.exit(0);
	}
    }

    // End of constructor

    //------------------------------------------------------------------------------------------------------------------------------------------------

    // Series of commands which define the enabled/disabled states of buttons following various commands

    // Carries out operations needed when SDSU is reset
    private void onResetSDSU(String resetType){
	startRun_enabled        = false;
	stopRun_enabled         = false;
	postApp_enabled         = false;
	resetSDSU_enabled       = false;
	resetPCI_enabled        = true;
	setupServer_enabled     = false;
	powerOn_enabled         = false;
	powerOff_enabled        = false;
	logPanel.add("Reset SDSU " + resetType, LogPanel.OK, true);
	_setEnabledActions();
    }

	// new reset feature as of NTT 2010 cycle 85
	private void onResetAll(){
		startRun_enabled        = false;
		stopRun_enabled         = false;
		postApp_enabled         = false;
		resetSDSU_enabled       = false;
		resetPCI_enabled        = true;
		setupServer_enabled     = false;
		powerOn_enabled         = false;
		powerOff_enabled        = false;
		logPanel.add("System reset", LogPanel.OK, true);
		_setEnabledActions();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Carries out operations needed when PCI is reset
    private void onResetPCI(){
	startRun_enabled        = false;
	stopRun_enabled         = false;
	postApp_enabled         = true;
	resetSDSU_enabled       = true;
	resetPCI_enabled        = false;
	setupServer_enabled     = true;
	powerOn_enabled         = false;
	powerOff_enabled        = false;
	logPanel.add("Reset PCI", LogPanel.OK, true);
	_setEnabledActions();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Carries out operations needed after servers setup
    private void onServersSet(){
	startRun_enabled        = false;
	stopRun_enabled         = false;
	postApp_enabled         = false;
	resetSDSU_enabled       = true;
	resetPCI_enabled        = false;
	setupServer_enabled     = false;
	powerOn_enabled         = true;
	powerOff_enabled        = false;
	logPanel.add("Servers setup", LogPanel.OK, true);
	_setEnabledActions();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Carries out operations needed after power on
    private void onPowerOn(){
	startRun_enabled        = false;
	stopRun_enabled         = false;
	postApp_enabled         = true;
	resetSDSU_enabled       = true;
	resetPCI_enabled        = false;
	setupServer_enabled     = false;
	powerOn_enabled         = false;
	powerOff_enabled        = true;
	logPanel.add("Powered on SDSU", LogPanel.OK, true);
	_setEnabledActions();

	boolean active = true;
	int n = 0;
	while(n < 5){
	    n++;
	    if((active = isRunActive(false)) && n < 5) 
		try { Thread.sleep(1000); } catch(Exception e){};
	} 
	if(active)
	    logPanel.add("Timed out waiting for 'power on' run to de-activate; cannot initialise run number. Stu, please tell me if this happens", LogPanel.ERROR, false);
	else
	    getRunNumber();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Carries out operations needed after power off
    private void onPowerOff(){
	startRun_enabled        = false;
	stopRun_enabled         = false;
	postApp_enabled         = false;
	resetSDSU_enabled       = true;
	resetPCI_enabled        = false;
	setupServer_enabled     = false;
	powerOn_enabled         = true;
	powerOff_enabled        = false;
	logPanel.add("Powered off SDSU", LogPanel.OK, true);
	_setEnabledActions();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Carries out operations needed when an application has been posted
    private void onPostApp(){
	startRun_enabled        = true;
	stopRun_enabled         = false;
	postApp_enabled         = true;
	resetSDSU_enabled       = false;
	resetPCI_enabled        = false;
	setupServer_enabled     = false;
	powerOn_enabled         = false;
	powerOff_enabled        = false;
	
	// Compute number of bytes and time per image for later use by exposure 
	// meters 
	_nbytesPerImage = nbytesPerImage();
	_timePerImage   = speed(CYCLE_TIME_ONLY);						       
	_nexposures     = numExpose;
	
	_postedTarget = _objectText.getText().trim();
	logPanel.add("Posted <strong>" + _postedTarget + "</strong> to servers", LogPanel.OK, true);
	_setEnabledActions();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Carries out operations needed when a run is started
    private void onStartRun(){

	logPanel.add("Started exposing on <strong>" + _postedTarget + "</strong>", LogPanel.OK, true);

	if(_dataFormat.hasChanged()) _unsavedSettings = true;
	if(_unsavedSettings && !EXPERT_MODE) _disableAll();

	startRun_enabled        = false;
	stopRun_enabled         = true;
	postApp_enabled         = false;
	resetSDSU_enabled       = false;
	resetPCI_enabled        = false;
	setupServer_enabled     = false;
	powerOn_enabled         = false;
	powerOff_enabled        = false;
	
	_exposureTime.setText("0");
	_spaceUsed.setText("0");
	
	incrementRunNumber();
	
	// This is just a safety measure in case the program has been restarted and
	// a run started without an application being posted
	if(_nbytesPerImage == 0){
	    // Compute number of bytes and time per image for later use by exposure 
	    // meters 
	    _nbytesPerImage = nbytesPerImage();
	    _timePerImage   = speed(CYCLE_TIME_ONLY);						       
	    _nexposures     = numExpose;
	}
	
	_exposureMeter.restart();
	
	if(_nexposures > 0){
	    
	    // In this case we want to start a timer to check whether a run
	    // is still active. Start by estimating length of time to avoid
	    // polling the server more than necessary
	    // Timer is activated once per second
	    
	    int pollInterval = Math.max(1000, (int)(1000*_timePerImage));
	    int initialDelay = Math.max(2000, (int)(1000*(_nexposures*_timePerImage-60.)));
	    if(DEBUG){
		System.out.println("Run polling Interval = " + pollInterval + " millseconds");
		System.out.println("Initial delay        = " + initialDelay + " milliseconds");
	    }
	    if(_runActive != null) _runActive.stop();
	    _runActive = new Timer(pollInterval, _checkRun);
	    _runActive.setInitialDelay(initialDelay);
	    _runActive.start();
	    
	}	
	_setEnabledActions();
	_ucamServersOn.setEnabled(false);

    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Carries out operations needed when a run is stopped
    private void onStopRun(){
	startRun_enabled        = false;
	stopRun_enabled         = false;
	postApp_enabled         = true;
	resetSDSU_enabled       = true;
	resetPCI_enabled        = false;
	setupServer_enabled     = false;
	powerOn_enabled         = false;
	powerOff_enabled        = true;
	_exposureMeter.stop();
	if(_runActive != null) _runActive.stop();
	logPanel.add("Stopped exposing on <strong>" + _postedTarget + "</strong>", LogPanel.OK, true);
	_setEnabledActions();
	_ucamServersOn.setEnabled(true);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // GUI update. It seems that updateComponentTree method re-enables the pastes on numeric fields where 
    // it was disabled. Thus we need to re-disable them all.
    public void updateGUI(){

	// Update colours
	SwingUtilities.updateComponentTreeUI(this);

	xbinText.setTransferHandler(null);
	ybinText.setTransferHandler(null);
	nblueText.setTransferHandler(null);
	exposeText.setTransferHandler(null);
	tinyExposeText.setTransferHandler(null);
	numExposeText.setTransferHandler(null);
	_magnitudeText.setTransferHandler(null);
	_seeingText.setTransferHandler(null);
	_airmassText.setTransferHandler(null);
	_windowPairs.disablePaste();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Sets the enabled state of any action buttons according to current values of equivalent boolean variables
    private void _setEnabledActions() {

	// Set the fine increment for expert mode
	tinyExposeText.setEnabled(EXPERT_MODE);

	enableChanges.setEnabled(EXPERT_MODE || _unsavedSettings);

	postApp.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || postApp_enabled));

	startRun.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || startRun_enabled));
	if(ULTRACAM_SERVERS_ON && (EXPERT_MODE || startRun_enabled))
	    startRun.setBackground(GO_COLOUR);
	else
	    startRun.setBackground(DEFAULT_COLOUR);
	
	stopRun.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || stopRun_enabled));
	if(ULTRACAM_SERVERS_ON && (EXPERT_MODE || stopRun_enabled))
	    stopRun.setBackground(STOP_COLOUR);
	else
	    stopRun.setBackground(DEFAULT_COLOUR);
	
	resetSDSUhard.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || resetSDSU_enabled));
	resetSDSUsoft.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || resetSDSU_enabled));
	resetAll.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || resetSDSU_enabled));

	resetPCI.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || resetPCI_enabled));

	execExpertCmd.setEnabled(ULTRACAM_SERVERS_ON && EXPERT_MODE);

	setupServer.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || setupServer_enabled));

	powerOn.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || powerOn_enabled));

	noddyPowerOff.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || powerOff_enabled));
	expertPowerOff.setEnabled(ULTRACAM_SERVERS_ON && (EXPERT_MODE || powerOff_enabled));

	if(_actionPanel != null){
	    _actionPanel.setEnabledAt(0, ULTRACAM_SERVERS_ON);
	    if(!ULTRACAM_SERVERS_ON)
		_actionPanel.setSelectedIndex(1);
	}

    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Enables the labels for the windows/window pairs
    private void _setWinLabels(boolean enable){
	ystartLabel.setEnabled(enable);
	xleftLabel.setEnabled(enable);
	xrightLabel.setEnabled(enable);
	nxLabel.setEnabled(enable);
	nyLabel.setEnabled(enable);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
	    
    /** Retrieves the values from the various fields and checks whether the currently 
     *  selected values represent a valid set of windows and sets. This should always
     *  be called by any routine that needs the most up-to-date values of the window parameters.
     */
    public boolean isValid(boolean loud) {

	_validStatus = true;

	try{

	    xbin      = xbinText.getValue();	
	    ybin      = ybinText.getValue();	
	    nblue     = nblueText.getValue();	
	    expose    = _getExpose();
	    numExpose = numExposeText.getValue();

	    setNumEnable();

	    _validStatus = _windowPairs.isValid(xbin, ybin, numEnable, loud);
	    
	}
	catch(Exception e){
	    if(loud)
		logPanel.add(e.toString(), LogPanel.ERROR, false);
	    _validStatus = false;
	}
	return _validStatus;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Get the exposure time from the two fields, one which gives the millsecond part, the other the 0.1 millsecond part
     * The application expects an integer number of 0.1 milliseconds
     */
    private int _getExpose() throws Exception {

	// In the case of ULTRACAM, the exposure time is specified in 0.1 milliseconds increments, but
	// this is too fine, so it is prompted for in terms of milliseconds.
	// This little program returns the value that must be sent to the servers.
	// In non-expert mode ensure expose is at least 5 (to get round a rare problem where it seems that
	// the tiny part can be set to zero)

	expose  = 10*exposeText.getValue() + tinyExposeText.getValue();
	if(!EXPERT_MODE) expose  = Math.max(5, expose);

	return expose;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Writes out a file which can be loaded into rtplot in order to define
     * windows. 'rtplot' can be set to load a file every new frame. This routine
     * writes one out in the correct format. It is rather superceded by the server
     * option which allows rtplot to interrogate this client directly.
     */
    public void saveToRtplot() {
	try{
	    if(_rtplotFile == null)
		throw new Exception("_rtplotFile is null in saveToRtplot");

	    if(isValid(true)){
		FileWriter fwriter = new FileWriter(_rtplotFile);
		fwriter.write("#\n# File written by Udriver\n#\n\n");
		fwriter.write("# xbin ybin\n" + xbin + " " + ybin + "\n");

		for(int i=0; i<numEnable; i++){
		    fwriter.write("\n# Window " + (2*i+1) + ", llx lly nx ny\n");
		    fwriter.write(_windowPairs.getXleftText(i) + " " + _windowPairs.getYstartText(i) + " " + 
				  _windowPairs.getNxText(i)     + " " + _windowPairs.getNyText(i) + "\n");
		    
		    fwriter.write("\n# Window " + (2*i+2) + ", llx lly nx ny\n");
		    fwriter.write(_windowPairs.getXrightText(i) + " " + _windowPairs.getYstartText(i) + " " + 
				  _windowPairs.getNxText(i)     + " " + _windowPairs.getNyText(i) + "\n");
		}

		fwriter.close();

		logPanel.add("Written rtplot windows to " + _rtplotFile.getName(), LogPanel.OK, false);

	    }else{
		logPanel.add("No rtplot windows written", LogPanel.ERROR, false);
	    }
	}
	catch(Exception e){
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    _showExceptionDialog(e);
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Writes out XML file suitable for defining the application and windows.
     * This requires there to be suitable example XML files available for each
     * application.
     */
    private void _saveApp() {

	try{
	    if(_xmlFile == null)
		throw new Exception("_saveApp: _xmlFile is null");

	    if(isValid(true)){

		if(_xmlFile.canWrite()){
		    int result = JOptionPane.showConfirmDialog(this, 
							       "Application file = " + _xmlFile.getName() + " already exists. Loss of data could occur if it is overwritten.\nDo you want to continue?", 
							       "Confirm file overwrite", JOptionPane.YES_NO_OPTION);
		    if(result == JOptionPane.NO_OPTION){
			logPanel.add("Application was not written to disk", LogPanel.WARNING, false);
			return;
		    }
		}

		Document document = _createXML(false);

		// Transform to write out
		FileWriter fwriter = new FileWriter(_xmlFile);

		_transformer.transform(new DOMSource(document), new StreamResult(fwriter));

		logPanel.add("Written application to <strong>" + _xmlFile.getName() + "</strong>", LogPanel.OK, false);
		_enableAll();
		_dataFormat.update();
		_unsavedSettings = false;

	    }else{
		throw new Exception("Invalid parameters; no XML file written");
	    }
	}
	catch(Exception e){
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    _showExceptionDialog(e);
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Load an application */
    private void _loadApp(boolean loadfilters) {

	try {

	    if(_xmlFile == null)
		throw new Exception("_loadApp: _xmlFile is null");

	    // Read file
	    Document document = _documentBuilder.parse(_xmlFile);
	    
	    // Try to determine application type
	    String appValue = null;
	    NodeList nlist = document.getElementsByTagName("executablecode");
	    for(int i=0; i<nlist.getLength(); i++){
		Element elem = (Element)nlist.item(i);
		if(elem.hasAttribute("xlink:href")) 
		    appValue = elem.getAttribute("xlink:href");
	    }
	    if(appValue == null)
		throw new Exception("Failed to locate application name from " + _xmlFile.getAbsolutePath());
	    
	    int iapp = 0;
	    for(iapp=0; iapp<TEMPLATE_ID.length; iapp++)
		if(TEMPLATE_ID[iapp].equals(appValue)) break;
	    if(iapp == TEMPLATE_ID.length)
		throw new Exception("Application type = \"" + appValue + "\" was not recognised in " + _xmlFile.getAbsolutePath());

	    applicationTemplate = TEMPLATE_LABEL[iapp];
	    templateChoice.setSelectedItem(applicationTemplate);

	    setNumEnable();

	    _windowPairs.setNpair(numEnable);

	    if(numEnable == 0)
		_setWinLabels(false);
	    else
		_setWinLabels(true);

	    // Now extract parameter values
	    boolean found_xbin = false, found_ybin = false;
	    boolean found_speed = false, found_nblue = false;
	    boolean found_expose = false;
	    boolean found_num_expose = false;
	    boolean[] found_ystart = {false, false, false};
	    boolean[] found_xleft  = {false, false, false};
	    boolean[] found_xright = {false, false, false};
	    boolean[] found_nx     = {false, false, false};
	    boolean[] found_ny     = {false, false, false};

	    // Needed to pick up null windows
	    int[] l3nx     = new int[2];
	    int[] l3ny     = new int[2];
	    int[] l3xstart = new int[2];
	    int[] l3ystart = new int[2];
	    
	    nlist = document.getElementsByTagName("set_parameter");
	    for(int i=0; i<nlist.getLength(); i++){
		Element elem = (Element)nlist.item(i);
		if(elem.hasAttribute("ref") && elem.hasAttribute("value")){

		    if(elem.getAttribute("ref").equals("X_BIN_FAC")) {
			xbinText.setText(elem.getAttribute("value"));
			found_xbin = true;
			
		    }else if(elem.getAttribute("ref").equals("Y_BIN_FAC")) {
			ybinText.setText(elem.getAttribute("value"));
			found_ybin = true;

		    }else if(elem.getAttribute("ref").equals("NBLUE")) {
			nblueText.setText(elem.getAttribute("value"));
			found_nblue = true;

		    }else if(elem.getAttribute("ref").equals("GAIN_SPEED")) {
			String gainSpeed = elem.getAttribute("value");
			found_speed = true;
			if(gainSpeed.equals(SLOW_SPEED)){
			    speedChoice.setSelectedItem("Slow");
			}else if(gainSpeed.equals(FAST_SPEED)){
			    speedChoice.setSelectedItem("Fast");
			}else if(gainSpeed.equals(TURBO_SPEED)){
			    speedChoice.setSelectedItem("Turbo");
			}else{
			    throw new Exception("Failed to recognise GAIN_SPEED = " + gainSpeed  + " in " + _xmlFile.getAbsolutePath());
			}

		    }else if(elem.getAttribute("ref").equals("EXPOSE_TIME")) {
			String evalue = elem.getAttribute("value").trim();

			if(evalue.length() > 1)
			    exposeText.setText(evalue.substring(0,evalue.length()-1));
			else
			    exposeText.setText("0");

			tinyExposeText.setText(evalue.substring(evalue.length()-1));
			
			found_expose = true;

		    }else if(elem.getAttribute("ref").equals("NO_EXPOSURES")) {
			numExposeText.setText(elem.getAttribute("value"));
			found_num_expose = true;

		    }else{

			for(int np=0; np<numEnable; np++){
			    if(elem.getAttribute("ref").equals("Y" + (np+1) + "_START")){
				_windowPairs.setYstartText(np,elem.getAttribute("value"));
				found_ystart[np] = true;
			    }else if(elem.getAttribute("ref").equals("X" + (np+1) + "L_START")){
				_windowPairs.setXleftText(np,elem.getAttribute("value"));
				found_xleft[np] = true;
			    }else if(elem.getAttribute("ref").equals("X" + (np+1) + "R_START")){
				_windowPairs.setXrightText(np,elem.getAttribute("value"));
				found_xright[np] = true;
			    }else if(elem.getAttribute("ref").equals("X" + (np+1) + "_SIZE")){
				_windowPairs.setNxText(np,elem.getAttribute("value"));
				found_nx[np]     = true;
			    }else if(elem.getAttribute("ref").equals("Y" + (np+1) + "_SIZE")){
				_windowPairs.setNyText(np,elem.getAttribute("value"));
				found_ny[np]     = true;
			    }
			}
		    }
		}
	    }
	    
	    // Check that all necessary parameters have been found
	    if(!found_xbin)
		throw new Exception("Failed to find X_BIN_FAC in " + _xmlFile.getAbsolutePath());
	    if(!found_ybin)
		throw new Exception("Failed to find Y_BIN_FAC in " + _xmlFile.getAbsolutePath());
	    if(!found_nblue)
		throw new Exception("Failed to find NBLUE in " + _xmlFile.getAbsolutePath());
	    if(!found_speed)
		throw new Exception("Failed to find GAIN_SPEED in " + _xmlFile.getAbsolutePath());
	    if(!found_expose)
		throw new Exception("Failed to find EXPOSE_TIME in " + _xmlFile.getAbsolutePath());
	    if(!found_num_expose)
		throw new Exception("Failed to find NO_EXPOSURES in " + _xmlFile.getAbsolutePath());

	    for(int i=0; i<numEnable; i++){
		if(!found_ystart[i]) 
		    throw new Exception("Failed to find ystart of " + WINDOW_NAME + " " + (i+1) + " in " + _xmlFile.getAbsolutePath());
		if(!found_xleft[i]) 
		    throw new Exception("Failed to find xleft of " + WINDOW_NAME + " " + (i+1) + " in " + _xmlFile.getAbsolutePath());
		if(!found_xright[i]) 
		    throw new Exception("Failed to find xright of " + WINDOW_NAME + " " + (i+1) + " in " + _xmlFile.getAbsolutePath());
		if(!found_nx[i]) 
		    throw new Exception("Failed to find nx of " + WINDOW_NAME + " " + (i+1) + " in " + _xmlFile.getAbsolutePath());
		if(!found_ny[i]) 
		    throw new Exception("Failed to find ny of " + WINDOW_NAME + " " + (i+1) + " in " + _xmlFile.getAbsolutePath());
	    }
	    
	    // Load user defined stuff
	    _setFromUser(document, "target",    _objectText);
	    //_setFromUser(document, "filters",   _filterText);
		if (loadfilters == true) {
			_setFilters(document);
		}
		_setRunType(document, "flags");
	    _setFromUser(document, "ID",        _progidText);
	    _setFromUser(document, "PI",        _piText);
	    _setFromUser(document, "Observers", _observerText);

	    logPanel.add("Loaded <strong>" + _xmlFile.getName() + "</strong>", LogPanel.OK, true);
	    _dataFormat.update();
	    _unsavedSettings = false;


	}
	catch(Exception e){
	    if(DEBUG) e.printStackTrace();
	    _showExceptionDialog(e);
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /* Sets the value of a JTextfield corresponding to 'item' from the 'user' section of 
     * an XML document. If not found, the JTextField is blanked.
     */
    private void _setFromUser(Document document, String item, JTextField field){
	NodeList nlist = document.getElementsByTagName("user");
	if(nlist.getLength() > 0){
	    Element  user = (Element)nlist.item(0);
	    NodeList newlist = user.getElementsByTagName(item);
	    if(newlist.getLength() > 0){
		Node node = newlist.item(0).getFirstChild();
		if(node != null){
		    String value = node.getNodeValue();
		    if(value != null){
			field.setText(value);
			return;
		    }
		}
	    }
	}

	// Failed somewhere, blank before returning
	field.setText("");

    }

    //------------------------------------------------------------------------------------------------------------------------------------------
	//
	private void _setFilters(Document document) {
		NodeList nlist = document.getElementsByTagName("user");
		if (nlist.getLength() > 0) {
			Element user = (Element)nlist.item(0);
			NodeList newlist = user.getElementsByTagName("filters");
			if(newlist.getLength() > 0) {
				Node node = newlist.item(0).getFirstChild();
				if (node != null) {
					String filters = node.getNodeValue();
					if (filters != null) {
						String[] filterarray = filters.split(" ");
						if (filterarray.length == 3) {
							_filter1.setSelectedItem(filterarray[0]);
							_filter2.setSelectedItem(filterarray[1]);
							_filter3.setSelectedItem(filterarray[2]);
						}
					}
				}
			}
		}
	}

    //------------------------------------------------------------------------------------------------------------------------------------------
	//
	private void _setRunType(Document document, String item) {
		NodeList nlist = document.getElementsByTagName("user");
		if (nlist.getLength() > 0) {
			Element user = (Element)nlist.item(0);
			NodeList newlist = user.getElementsByTagName(item);
			if (newlist.getLength() > 0) {
				Node node = newlist.item(0).getFirstChild();
				if (node != null) {
					String value = node.getNodeValue();
					_acquisitionState = false;
					if (value.indexOf("data") > -1) {
						_dataButton.setSelected(true);
						_runType = "data";
					}
					if (value.indexOf("caution") > -1) {
						_acqButton.setSelected(true);
						_runType = "data";
						_acquisitionState = true;
					}
					if (value.indexOf("bias") > -1) {
						_runType = "bias";
						_biasButton.setSelected(true);
					}
					if (value.indexOf("flat") > -1) {
						_runType = "flat";
						_flatButton.setSelected(true);
					}
					if (value.indexOf("dark") > -1) {
						_runType = "dark";
						_darkButton.setSelected(true);
					}
					if (value.indexOf("technical") > -1) {
						_runType = "technical";
						_techButton.setSelected(true);
					}
					_checkEnabledFields();
				}
			}
		}

	}
    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Posts application corresponding to current settings to servers
     * This requires there to be suitable example XML files available for each
     * application.
     */
    private boolean _postApp() {
	
	try{
	    if(isValid(true)){
		
		Document document = _createXML(true);

		// First to the camera server
		HttpURLConnection httpConnection = (HttpURLConnection)(new URL(HTTP_CAMERA_SERVER + HTTP_PATH_CONFIG).openConnection());
		httpConnection.setRequestMethod("POST");
		httpConnection.setRequestProperty("Content-Type", "text/xml");
		httpConnection.setDoOutput(true);
		httpConnection.connect();
		
		OutputStream outputStream = httpConnection.getOutputStream();
		_transformer.transform(new DOMSource(document), new StreamResult(outputStream));
		
		// RDGH 26/03/2010 : Add a timeout for this connection
		// Prevents client hanging from camera bug
		httpConnection.setConnectTimeout(5000);
		httpConnection.setReadTimeout(5000);
		// Get reply back from server
		InputStream  inputStream  = httpConnection.getInputStream();
		String       xmlString    = _readText(inputStream).trim();
		
		Document xmlDoc = _documentBuilder.parse(new InputSource(new StringReader(xmlString)));
		_replyPanel.showReply(xmlDoc, HTTP_CAMERA_SERVER, true, EXPERT_MODE);

		if(!isResponseOK(xmlDoc))
		    throw new Exception("XML response from camera server = " + HTTP_CAMERA_SERVER + " was not OK");


		// Now to the data server
		httpConnection = (HttpURLConnection)(new URL(HTTP_DATA_SERVER + HTTP_PATH_CONFIG).openConnection());
		httpConnection.setRequestMethod("POST");
		httpConnection.setRequestProperty("Content-Type", "text/xml");
		httpConnection.setDoOutput(true);
		httpConnection.connect();
			
		outputStream = httpConnection.getOutputStream();
		_transformer.transform(new DOMSource(document), new StreamResult(outputStream));
		
		// Get reply back from server
		inputStream  = httpConnection.getInputStream();
		xmlString    = _readText(inputStream).trim();
		
		xmlDoc = _documentBuilder.parse(new InputSource(new StringReader(xmlString)));
		_replyPanel.showReply(xmlDoc, HTTP_DATA_SERVER, false, EXPERT_MODE);

		if(!isResponseOK(xmlDoc))
		    throw new Exception("XML response from data server = " + HTTP_DATA_SERVER + " was not OK");

	    }else{
		throw new Exception("Windows invalid; application was not posted to the servers");
	    }	
	    return true;
	}
	catch(Exception e) {
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    _showExceptionDialog(e);
	    return false;
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Fetches an application from the server, returning a Document as the result.
     *  If it fails, error messages will be printed and the Document returned will
     * be null
     */
    private Document _fetchApp(String name) {

	String xmlString = null;

	try {
	    URL url = new URL(HTTP_CAMERA_SERVER + HTTP_PATH_GET + "?" + HTTP_SEARCH_ATTR_NAME + "=" + name);

	    xmlString = _readText(url.openStream()).trim();

	    Document document = _documentBuilder.parse(new InputSource(new StringReader((xmlString))));
	    
	    logPanel.add("Application = <strong>" + name + "</strong> fetched from server.", LogPanel.OK, true);

	    return document;
	}
	catch(SocketException e) {
	    if(DEBUG) e.printStackTrace();
	    String message = "A SocketException can be caused if an invalid CGI argument is used after \"" +
		HTTP_PATH_GET + "?\" in the URL.\n" +
		"Make sure \"" + HTTP_CAMERA_SERVER + HTTP_PATH_GET + "?" + HTTP_SEARCH_ATTR_NAME + "=<file>\" is supported by the server.\n" +
		"And check whether \"" +  HTTP_CAMERA_SERVER + HTTP_PATH_GET + "\" is supported by the server.";
	    
	    JOptionPane.showMessageDialog(this, message, "SocketException", JOptionPane.WARNING_MESSAGE);
	}
	catch(SAXParseException e) {
	    if(DEBUG) e.printStackTrace();
	    System.out.println("XML start\n" + xmlString + "\nXML end");
	    JOptionPane.showMessageDialog(this, e + "\nTry again.", e.getClass().getName(), JOptionPane.WARNING_MESSAGE);
	}
	catch(Exception e) {
	    if(DEBUG) e.printStackTrace();
	    JOptionPane.showMessageDialog(this, e + "\nTry again.", e.getClass().getName(), JOptionPane.WARNING_MESSAGE);
	}
	return null;
	
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Read from an InputStream into a string. Go via an InputStreamReader to translate bytes
     * to chars */
    private static String _readText(InputStream inputStream) {
	try{
	    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
	    char[]       cbuff = new char[2048];
	    StringBuffer sbuff = new StringBuffer();
	    int len;
		while((len = inputStreamReader.read(cbuff)) != -1){
			sbuff.append(cbuff, 0, len);
	    }
	    return sbuff.toString();
	}
	catch(Exception e){
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    return "_readText failed";
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** This loads an XML template file and updates it according to the 
     *  values of the settings of the window panel. The template file
     * must match the current application.
     */
    private Document _createXML(boolean posting) throws Exception {
	
	try{

	    if(isValid(true)){
		
		Document document = null;
		if(TEMPLATE_FROM_SERVER){
		    
		    document = _fetchApp(TEMPLATE_APP[_whichTemplate()]);
		    
		} else {
		    
		    // Read & parse example file
		    File templateFile = new File(TEMPLATE_DIRECTORY + TEMPLATE_APP[_whichTemplate()]);
		    document = _documentBuilder.parse(templateFile);

		}
		if(document == null)
		    throw new Exception("document = null");
		
		// Try to determine application type
		String appValue = null;
		NodeList nlist = document.getElementsByTagName("executablecode");
		for(int i=0; i<nlist.getLength(); i++){
		    Element elem = (Element)nlist.item(i);
		    if(elem.hasAttribute("xlink:href")) 
			appValue = elem.getAttribute("xlink:href");
		}
		if(appValue == null)
		    throw new Exception("failed to locate application name");
		
		if(!appValue.equals(TEMPLATE_ID[_whichTemplate()]))
		    throw new Error("application name = \"" + appValue + 
				    "\" does not match the current template value = " +
				    TEMPLATE_ID[_whichTemplate()]);
		
		// Modify the window values. NB although this code looks the 
		// same as that in _loadApp, it differs in that it is the DOM
		// document parameters that are changed to match the window GUI
		// settings rather than the other way around. The input XML
		// is used to define the basic structure that can be edited in this
		// manner.

		boolean found_xbin       = false, found_ybin = false;
		boolean found_speed      = false, found_nblue = false;
		boolean found_expose     = false;
		boolean found_num_expose = false;
		boolean[] found_ystart   = {false, false, false};
		boolean[] found_xleft    = {false, false, false};
		boolean[] found_xright   = {false, false, false};
		boolean[] found_nx       = {false, false, false};
		boolean[] found_ny       = {false, false, false};
		
		NodeList inst = document.getElementsByTagName("set_parameter");
		for(int i=0; i<inst.getLength(); i++){
		    Element elem = (Element)inst.item(i);
		    if(elem.hasAttribute("ref") && elem.hasAttribute("value")){

			if(elem.getAttribute("ref").equals("X_BIN_FAC")) {
			    elem.setAttribute("value",  String.valueOf(xbin));
			    found_xbin = true;

			}else if(elem.getAttribute("ref").equals("Y_BIN_FAC")) {
			    elem.setAttribute("value",  String.valueOf(ybin));
			    found_ybin = true;

			}else if(elem.getAttribute("ref").equals("NBLUE")) {
			    elem.setAttribute("value",  String.valueOf(nblue));
			    found_nblue = true;

			}else if(elem.getAttribute("ref").equals("GAIN_SPEED")) {

			    if(speedChoice.getSelectedItem().equals("Slow")){
				elem.setAttribute("value",  SLOW_SPEED);
			    }else if(speedChoice.getSelectedItem().equals("Fast")){
				elem.setAttribute("value",  FAST_SPEED);
			    }else if(speedChoice.getSelectedItem().equals("Turbo")){
				elem.setAttribute("value",  TURBO_SPEED);
			    }else{
				throw new Exception("current speed choice = " + speedChoice.getSelectedItem() +
						    " is not valid.");
			    }
			    found_speed = true;

			}else if(elem.getAttribute("ref").equals("EXPOSE_TIME")) {
			    elem.setAttribute("value",  String.valueOf(expose) );
			    found_expose = true;

			}else if(elem.getAttribute("ref").equals("NO_EXPOSURES")) {
			    elem.setAttribute("value",  String.valueOf(numExpose));
			    found_num_expose = true;

			}else{
			    
			    for(int np=0; np<numEnable; np++){
				if(elem.getAttribute("ref").equals("Y" + (np+1) + "_START")){
				    elem.setAttribute("value",  _windowPairs.getYstartText(np));
				    found_ystart[np] = true;
				}else if(elem.getAttribute("ref").equals("X" + (np+1) + "L_START")){
				    elem.setAttribute("value",  _windowPairs.getXleftText(np));
				    found_xleft[np] = true;
				}else if(elem.getAttribute("ref").equals("X" + (np+1) + "R_START")){
				    elem.setAttribute("value",  _windowPairs.getXrightText(np));
				    found_xright[np] = true;
				}else if(elem.getAttribute("ref").equals("X" + (np+1) + "_SIZE")){
				    elem.setAttribute("value",  _windowPairs.getNxText(np));
				    found_nx[np]     = true;
				}else if(elem.getAttribute("ref").equals("Y" + (np+1) + "_SIZE")){
				    elem.setAttribute("value",  _windowPairs.getNyText(np));
				    found_ny[np]     = true;
				}
			    }

			}
		    }
		}

		// Check that all necessary parameters have been found
		if(!found_xbin)
		    throw new Exception("failed to find X_BIN_FAC");
		if(!found_ybin)
		    throw new Exception("failed to find Y_BIN_FAC");
		if(!found_nblue)
		    throw new Exception("failed to find NBLUE");
		if(!found_speed)
		    throw new Exception("failed to find GAIN_SPEED");
		if(!found_expose)
		    throw new Exception("failed to find EXPOSE_TIME");
		if(!found_num_expose)
		    throw new Exception("failed to find NO_EXPOSURES");
		
		for(int i=0; i<numEnable; i++){
		    if(!found_ystart[i]) 
			throw new Exception("failed to find & modify ystart of " + WINDOW_NAME + " " + (i+1));
		    if(!found_xleft[i]) 
			throw new Exception("failed to find & modify xleft of " + WINDOW_NAME + " " + (i+1));
		    if(!found_xright[i]) 
			throw new Exception("failed to find & modify xright of " + WINDOW_NAME + " " + (i+1));
		    if(!found_nx[i]) 
			throw new Exception("failed to find & modify nx of " + WINDOW_NAME + " " + (i+1));
		    if(!found_ny[i]) 
			throw new Exception("failed to find & modify ny of " + WINDOW_NAME + " " + (i+1));
		}
		
		// Now add user stuff
		Element rootElement = document.getDocumentElement();
		Element userElement = document.createElement("user");
		rootElement.appendChild(userElement);

		String target = "";
		String progid = "";
		String pi = "";
		if (_runType == "data" || _runType == "technical") {
			target = _objectText.getText();
			progid = _progidText.getText();
			pi = _piText.getText();
		} else {
			if (_runType.length() > 0) {
				target = _runType.substring(0,1).toUpperCase() + _runType.substring(1);
			}
			progid = pi = "Calib";
		}

		_addToUser(document, userElement, "target",    target);
		//_addToUser(document, userElement, "filters",   _filterText.getText());
		_addToUser(document, userElement, "filters", _filter1.getSelectedItem() + " " + _filter2.getSelectedItem() + " " + _filter3.getSelectedItem());
		_addToUser(document, userElement, "ID",        progid);
	    _addToUser(document, userElement, "PI",        pi);
		_addToUser(document, userElement, "Observers", _observerText.getText());
		String flags = _runType;
		if (_acquisitionState) {
			flags = flags + " " + "caution";
		}
		_addToUser(document, userElement, "flags", flags);

		// now use readback to try and get the current VERSION/REVISION
		// only do this if we don't have a version yet
		// this gets around limitation in the camera server which will
		// STOP a run if it receives another command while running (!)
		if (ULTRACAM_SERVERS_ON && posting && SERVER_READBACK_VERSION == 0) {
			int verDecimal = -1;
			String verReadback = "";
			try {
				URL url = new URL(HTTP_CAMERA_SERVER + HTTP_PATH_EXEC + "?RM,X,0x80");
				// readback is an xml attribute of command_status
				String xmlString = _readText(url.openStream()).trim();
				Document xmlDoc = _documentBuilder.parse(new InputSource(new StringReader(xmlString)));
				Node commandNode = xmlDoc.getElementsByTagName("command_status").item(0);
				NamedNodeMap commandAttributes = commandNode.getAttributes();
				for (int i = 0; i < commandAttributes.getLength(); i++) {
						String nodeName = commandAttributes.item(i).getNodeName().trim();
						String nodeValue = commandAttributes.item(i).getNodeValue();
						if (nodeName.equalsIgnoreCase("readback")) {
							verReadback = nodeValue;
							break;
						}
				}
				if (verReadback.equals(""))
					System.out.println("Didn't find readback in camera XML?");
			} catch (Exception e) { System.out.println("Couldn't interrogate camera server for version readback.");}

			// readback is in hex ie. "0xFF", convert to decimal
			try {
				if (verReadback.substring(0,2).equals("0x"))
					verReadback = verReadback.substring(2);
				SERVER_READBACK_VERSION = Integer.parseInt(verReadback,16);
			} catch (Exception e) { System.out.println("Couldn't convert readback version to decimal."); }
		}

		if (posting)
			_addToUser(document,userElement,"revision",Integer.toString(SERVER_READBACK_VERSION));


		// Grab temperature data from Andy's Server
		if(DATA_FROM_IMEDIA1){
		    try{
			URL tempURL = new URL("http://192.168.1.3/temperature/latest_temperature.txt");
			String tempString = _readText(tempURL.openStream()).trim();
			int greenStart = tempString.indexOf("Green");
			int greenEnd   = tempString.indexOf("\n",greenStart);
			int blueStart = tempString.indexOf("Blue");
			int blueEnd   = tempString.indexOf("\n",blueStart);
			int redStart = tempString.indexOf("Red");
			int redEnd   = tempString.indexOf("\n",redStart);
			_addToUser(document, userElement, "RedTempData", tempString.substring(redStart+16, redEnd));
			_addToUser(document, userElement, "GreenTempData", tempString.substring(greenStart+18, greenEnd));
			_addToUser(document, userElement, "BlueTempData", tempString.substring(blueStart+17, blueEnd));
		    }catch(Exception e){
			// warn
			JOptionPane.showMessageDialog(this,
						      "Failed to get CCD temperatures from imedia PC",
						      "Udriver Warning",
						      JOptionPane.WARNING_MESSAGE);
		    }
		    // Grab slide position info from Slide CGI script
		    try{
			URL slideURL = new URL("http://192.168.1.3/slide/slide.cgi?position");
			String slideString = _readText(slideURL.openStream()).trim();
			int slideStart = slideString.lastIndexOf(",");
			int slideEnd   = slideString.indexOf("\n",slideStart);
			_addToUser(document, userElement, "SlidePos", slideString.substring(slideStart+2, slideEnd));
			_addBlankLine(document, userElement);
			_addBlankLine(document, rootElement);
		    }catch(Exception e){
			// warn
			JOptionPane.showMessageDialog(this,
						      "Failed to get slide position from imedia PC",
						      "Udriver Warning",
						      JOptionPane.WARNING_MESSAGE);
		    }
		}
		return document;
		
	    }else{
		throw new Exception("current settings are invalid; application was not edited");
	    }	
	}
	catch(Exception e){
	    throw new Exception("_createXML: " + e);
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /* Adds another XML tag of form <item>value</item> below element in the
     * XML document document. A blank line is inserted before each item */
    private void _addToUser(Document document, Element element, String item, String value){

	_addBlankLine(document, element);

	_addSpace(document, element, 4);
	Element newElement = document.createElement(item);
	Text elementText   = document.createTextNode(value);
	newElement.appendChild(elementText);
	element.appendChild(newElement);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
	//
	private void _checkEnabledFields() {
		boolean enable = true;
		if (_runType == "data" || _runType == "technical") {
			enable = true;
		} else {
			enable = false;
		}
		if (_acquisitionState == true) {
			_objectText.setBackground(WARNING_COLOUR);
		} else {
			_objectText.setBackground(DEFAULT_COLOUR);
		}
		_piText.setEnabled(enable);
		_progidText.setEnabled(enable);
		_objectText.setEnabled(enable);
	}

    //------------------------------------------------------------------------------------------------------------------------------------------

    /* Adds a blank line to an element to give a nicer looking format */
    private void _addBlankLine(Document document, Element element) {
	Text blankLine = document.createTextNode("\n\n");
	element.appendChild(blankLine);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /* Adds nspace spaces an element to give a nicer looking format */
    private void _addSpace(Document document, Element element, int nspace) {
	StringBuffer buff = new StringBuffer();
	for(int i=0; i<nspace; i++)
	    buff.append(" ");
	Text spaces = document.createTextNode(buff.toString());
	element.appendChild(spaces);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Execute a remote application */
    private boolean _execRemoteApp(String application, boolean reset) {

	String xmlString = null;

	try {
	    URL url = new URL(HTTP_CAMERA_SERVER + HTTP_PATH_CONFIG + "?" + application);
	    xmlString = _readText(url.openStream()).trim();
	    Document document = _documentBuilder.parse(new InputSource(new StringReader(xmlString)));
	    _replyPanel.showReply(document, "Response to executing application = " + application + " on camera server", reset, EXPERT_MODE);

	    if(!isResponseOK(document))
		throw new Exception("XML response from camera server = " + HTTP_CAMERA_SERVER + " was not OK");

	    url = new URL(HTTP_DATA_SERVER + HTTP_PATH_CONFIG + "?" + application);
	    xmlString = _readText(url.openStream()).trim();
	    document = _documentBuilder.parse(new InputSource(new StringReader(xmlString)));
	    _replyPanel.showReply(document, "Response to executing application = " + application + " on data server", false, EXPERT_MODE);

	    if(!isResponseOK(document))
		throw new Exception("XML response from data server = " + HTTP_DATA_SERVER + " was not OK");

	    logPanel.add("Executed <strong>" + application + "</strong> on both servers", LogPanel.OK, true);

	    return true;
	}
	catch(SocketException e) {
       	    if(DEBUG) e.printStackTrace();
	    String message = "A SocketException can be caused if an invalid CGI argument is used after \"" +
		HTTP_PATH_CONFIG + "?\" in the URL.\n" +
		"Make sure \"" + HTTP_PATH_CONFIG + "?" + application + "\" is supported by the servers.\n" +
		"And check whether \"" + HTTP_PATH_CONFIG + "\" is supported by the servers.";
	    
	    JOptionPane.showMessageDialog(this, message, "SocketException", JOptionPane.WARNING_MESSAGE);
	}
	catch(SAXParseException e) {
       	    if(DEBUG) e.printStackTrace();
	    System.out.println("XML start\n" + xmlString + "\nXML end");
	    JOptionPane.showMessageDialog(this, e + "\nTry again.", e.getClass().getName(), JOptionPane.WARNING_MESSAGE);
	}
	catch(Exception e) {
	    if(DEBUG) e.printStackTrace();
	    JOptionPane.showMessageDialog(this, e + "\nTry again.", e.getClass().getName(), JOptionPane.WARNING_MESSAGE);
	}
	logPanel.add("Failed to execute <strong>" + application + "</strong>", LogPanel.ERROR, false);
	return false;
	
    }

	private boolean _verifyTarget(String target) {
		boolean exists = false;
        /*
		if (USE_UAC_DB) {
			exists = _uacExists(target);
		}
		if (!exists) {
			exists = _simbadExists(target);
		}
		if (!exists) {
			if (USE_UAC_DB) {
				logPanel.add("Could not find target in UAC database or SIMBAD.",LogPanel.ERROR,false);
			} else {
				logPanel.add("SIMBAD lookup <strong>failed.</strong>",LogPanel.ERROR,false);
                }
		}
        */
        logPanel.add("SIMBAD lookup disabled",LogPanel.ERROR,false);
		return exists;
	}

	private boolean _uacExists(String target) {
		Connection conn = null;
		int count = 0;
		try {
			//Class.forName("com.mysql.jdbc.Driver").newInstance();
			String usern = "ultracam";
			String passw = "nogales";
			String url = "jdbc:mysql://" + UAC_DATABASE_HOST + "/uac";
			conn = DriverManager.getConnection(url,usern,passw);
			Statement s = conn.createStatement();
			s.executeQuery("SELECT id FROM objects WHERE names LIKE '%" + target + "%' LIMIT 1;");
			ResultSet rs = s.getResultSet();
			while (rs.next()) {
				++count;
				logPanel.add("Found UAC id <strong>" + rs.getString("id") + ".</strong>",LogPanel.OK,true);
			}
		} catch (SQLException e) { System.out.println(e);}
		finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {}
			}
		}
		if (count > 0) {
			return true;
		} else {
			return false;
		}
	}
    //------------------------------------------------------------------------------------------------------------------------------------------
	//
	private boolean _simbadExists(String target) {
        int TIMEOUT = 2000; // timeout in millisecs
        String script = null;
        String result = null;
        script = "set limit 1\n";
        script = script + "format object form1 \"%IDLIST(1) : %COO(A) : %COO(D)\"\n";
        script = script + "echodata ** UDRIVER QUERY\n";
        script = script + "query id " + target + "\n";
        try{
			script = URLEncoder.encode(script,"ISO-8859-1");
			URL simbadurl = new
            URL("http://simbad.u-strasbg.fr/simbad/sim-script?submit=submit+script&script="
                + script);
            URLConnection simbadcon = simbadurl.openConnection();
            simbadcon.setConnectTimeout(TIMEOUT);
            simbadcon.setReadTimeout(TIMEOUT);
            result = _readText(simbadcon.getInputStream());
            //System.out.println(result);

            String [] simbad = result.split("\n");
            int startline = 0;
            for (int i = 0 ; i < simbad.length ; i++) {
                if (simbad[i].indexOf("::data::") > -1) {
                    startline = i;
                }
                if (simbad[i].indexOf("::error::") > -1) {
                    System.out.println("Encountered simbad error");
                    return false;
                }
            }
            for (int i = startline ; i < simbad.length ; i++) {
                if (simbad[i].indexOf("** UDRIVER QUERY") > -1) {
                    if (simbad.length > (i+1)) {
                        if (simbad[i+1].split(":").length == 3) {
                            logPanel.add(
                                         "SIMBAD lookup <strong>success.</strong>",
                                         LogPanel.OK,true);
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            return true;

        } catch(SocketTimeoutException ste) {
            System.out.println("simbad request timed out (>" + TIMEOUT +
                               " milliseconds)");
        } catch(UnknownHostException uhe) {
            System.out.println(uhe);
        } catch(UnsupportedEncodingException uee) {
            System.out.println(uee);
        } catch(MalformedURLException mue) {
            System.out.println(mue);
        } catch(IOException ioe) {
            System.out.println(ioe);
        }
        return false;
	}	
	//
    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Execute a command. This is method that sends the requests to start and stop
     * runs etc.
     * @param command "GO" to start a run, "ST" to stop it, "RCO" to reset timing board,
     * "RST" to reset the PCI board  
     */

    private boolean _execCommand(String command, boolean reset) {

	logPanel.add("Sent command <strong>" + command + "</strong>", LogPanel.OK, true);

	String xmlString = null;

	try {

	    URL url = new URL(HTTP_CAMERA_SERVER + HTTP_PATH_EXEC + "?" + command);

	    xmlString = _readText(url.openStream()).trim();

	    Document xmlDoc = _documentBuilder.parse(new InputSource(new StringReader(xmlString)));

	    _replyPanel.showReply(xmlDoc, "Response to command = " + command, reset, EXPERT_MODE);
	    if(!isResponseOK(xmlDoc))
		throw new Exception("XML response from camera server " + HTTP_CAMERA_SERVER + " was not OK");

	    logPanel.add("Executed command <strong>" + command + "</strong>", LogPanel.OK, true);

	    return true;

	}
	catch(SocketException e) {
	    if(DEBUG) e.printStackTrace();
	    JOptionPane.showMessageDialog(this, "Check that the server = " + HTTP_CAMERA_SERVER + " is active", "SocketException", JOptionPane.WARNING_MESSAGE);
	}
	catch(SAXParseException e) {
	    if(DEBUG) e.printStackTrace();
	    System.out.println("XML start\n" + xmlString + "\nXML end");
	    JOptionPane.showMessageDialog(this, e + "\nTry again.", e.getClass().getName(), JOptionPane.WARNING_MESSAGE);
	}
	catch(Exception e) {
	    if(DEBUG) e.printStackTrace();
	    JOptionPane.showMessageDialog(this, e + "\nTry again.", e.getClass().getName(), JOptionPane.WARNING_MESSAGE);
	}
	logPanel.add("Failed to execute command <strong>" + command + "</strong>", LogPanel.ERROR, false);
	return false;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Sends an application to a server **/
    void _initServer(String server, String application, boolean reset) throws Exception {
	URL      url       =  new URL(server + HTTP_PATH_CONFIG + "?" + application);
	String   xmlString = _readText(url.openStream()).trim();
	Document document  = _documentBuilder.parse(new InputSource(new StringReader(xmlString)));
	_replyPanel.showReply(document, "Response to " + application, reset, EXPERT_MODE);
	if(!isResponseOK(document))
	    throw new Exception("XML response from camera server = " + server + " to application " + application + " was not OK");
    }

    /** Initialise the servers */
    private boolean _setupServers(boolean reset) {
	try {
	    
	    _initServer(HTTP_CAMERA_SERVER, _telescope.application, reset);

	    _initServer(HTTP_CAMERA_SERVER, GENERIC_APP, false);

	    _initServer(HTTP_DATA_SERVER, _telescope.application, false);

	    _initServer(HTTP_DATA_SERVER, GENERIC_APP, false);

	    return true;
	}
	catch(Exception e) {
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    logPanel.add("Failed to setup servers", LogPanel.ERROR, false);
	    _showExceptionDialog(e);
	    return false;
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Polls the data server to see if a run is active */
    public boolean isRunActive(boolean quiet) {
	try { 
	    URL url           = new URL(HTTP_DATA_SERVER + "status");
	    String reply      = _readText(url.openStream()).trim();
	    Document document = _documentBuilder.parse(new InputSource(new StringReader(reply)));
	    NodeList nlist    = document.getElementsByTagName("state");
	    if(nlist.getLength() == 0)
		throw new Exception("Could not find 'state' element in XML returned from the server");
	    
	    Element element   = (Element)nlist.item(0);
	    if(element.hasAttribute("server")){
		if(element.getAttribute("server").equals("IDLE")){
		    return false;
		}else if(element.getAttribute("server").equals("BUSY")){
		    return true;
		}else{
		    throw new Exception("Failed to interpret 'state' value from server = " + element.getAttribute("server"));
		}
	    }else{
		throw new Exception("'state' element in XML from server did not have 'server' attribute");
	    }
	}
	catch(Exception e){
	    if(!quiet){
		logPanel.add(e.toString(), LogPanel.ERROR, false);
		logPanel.add("Will assume that a run IS active", LogPanel.ERROR, false);
	    }
	    return true;
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Gets the run number */
    public void getRunNumber() {
	try {
	    URL url           = new URL(HTTP_DATA_SERVER + "fstatus");
	    String reply      = _readText(url.openStream()).trim();
	    Document document = _documentBuilder.parse(new InputSource(new StringReader(reply)));
	    NodeList nlist    = document.getElementsByTagName("lastfile");
	    if(nlist.getLength() == 0)
		throw new Exception("Could not find 'lastfile' element in XML returned from the server");
	    
	    Element element   = (Element)nlist.item(0);
	    if(element.hasAttribute("path")){
		String path   = element.getAttribute("path").trim();
		if(path.length() > 2){
		    String numString = path.substring(path.length()-3);
		    int number = Integer.parseInt(numString);
		    _runNumber.setText(String.valueOf(number));
		}else{
		    throw new Exception("Path = " + path + " not long enough for 3 digit run number");
		}
	    }else{
		throw new Exception("'lastfile' element in XML from server does not have 'path' attribute");
	    }
	}
	catch(Exception e) {
	    logPanel.add("Failed to determine run number; will be set blank", LogPanel.ERROR, false);
	    System.out.println(e);
	    _runNumber.setText("");
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Increment run number */
    public void incrementRunNumber(){	
	try {
	    String numString = _runNumber.getText();
	    if(numString.equals(""))
		throw new Exception("Run number is blank, which means that it cannot be incremented");
	    int number = Integer.parseInt(numString);
	    number++;
	    _runNumber.setText(String.valueOf(number));
	}
	catch(Exception e) {
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    _runNumber.setText("");
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Tests whether XML response from server is OK or not
     * It does so by looking for an element of the form
     * <status> and then looking for 'software' and possibly
     * 'camera' attrributes depending upon the source
     */
    public boolean isResponseOK(Document document){
	
	try{

	    NodeList nlist = document.getElementsByTagName("source");
	    if(nlist.getLength() == 0)
		throw new Exception("Could not find 'source' element in XML returned from the server");   
	    Node node = nlist.item(0).getFirstChild();
	    if(node == null)
		throw new Exception("'source' had no children in XML returned from the server");   

	    String source = node.getNodeValue().trim();
	    if(source == null)
		throw new Exception("'source' value was null in XML returned from the server");   

	    // Need software="OK" in <status> tag
	    nlist    = document.getElementsByTagName("status");
	    if(nlist.getLength() == 0)
		throw new Exception("Could not find 'status' element in XML returned from the server");
	    Element element   = (Element)nlist.item(0);
	    if(element.hasAttribute("software")){
		if(!element.getAttribute("software").equals("OK"))
		    throw new Exception("'software' attribute of 'status' element = " + element.getAttribute("software") + " not = OK from source = " + source);
	    }else{
		throw new Exception("Could not find 'software' attribute of 'status' element from source = " + source);
	    }

	    if(source.equals("Camera server")){

		// Need camera="OK" in <status> tag
		if(element.hasAttribute("camera")){
		    if(!element.getAttribute("camera").equals("OK"))
			throw new Exception("'camera' attribute of 'status' element = " + element.getAttribute("camera") + " not = OK from source = " + source);
		}else{
		    throw new Exception("Could not find 'camera' attribute of 'status' element from source = " + source);
		}

	    }else if(!source.equals("Filesave data handler")){

		   throw new Exception("source = " + source + " not recognised. Expected either 'Camera server' or 'Filesave data handler'");
	    }
	    
	    return true;
	}
	catch(Exception e){
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    return false;
	}
    }
	    
    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Returns the index of the current application. Should be done with a map
     * but this will have to do for now.
     */
    private int _whichTemplate(){
	int iapp = 0;
	for(iapp=0; iapp<TEMPLATE_LABEL.length; iapp++)
	    if(applicationTemplate.equals(TEMPLATE_LABEL[iapp])) break;
	if(iapp == TEMPLATE_LABEL.length){
	    System.out.println("Template = " + applicationTemplate + " not recognised.");
	    System.out.println("This is a programming or configuration file error and the program will terminate.");
	    System.exit(0);
	}
	return iapp;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
		
    /** Sets the number of windows/window pairs in use */
    public void setNumEnable(){
	try{
	    numEnable = Integer.parseInt(TEMPLATE_PAIR[_whichTemplate()]);
	}
	catch(Exception e){
	    e.printStackTrace();
	    System.out.println(e);
	    System.out.println("Probable error in TEMPLATE_PAIR in configuration file = " + CONFIG_FILE);
	    System.out.println("This is a programming or configuration file error and the program will terminate.");
	    System.exit(0);
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** This routine implements's Vik's speed computations and reports
     *	the frame rate in Hertz, the cycle time (e.g. sampling time),
     * exposure time (time on source per cycle), the dead time and readout
     * time, all in seconds. Finally it also reports the duty cycle, and
     * in the case of drift mode, the number of windows in the storage area
     * along with the pipe shift in pixels.
     */

    public double speed(int method) {

	try{

	    if(isValid(_validStatus)){
		
		// Set the readout speed
		readSpeed = (String) speedChoice.getSelectedItem();
		double cdsTime, clearTime, frameTransfer, readout, video;
		double cycleTime, frameRate, exposureTime, deadTime;
		int nwins = 0, pshift = 0;
		    
		if(readSpeed.equals("Fast")){
		    cdsTime = CDS_TIME_FBB;
		}else if(readSpeed.equals("Turbo")){
		    cdsTime = CDS_TIME_FDD;
		}else if(readSpeed.equals("Slow")){
		    cdsTime = CDS_TIME_CDD;
		}else{
		    throw new Error("readSpeed = \"" + readSpeed + "\" is unrecognised. Programming error");
		}
		video = cdsTime + SWITCH_TIME;
		
		if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){
		    
		    frameTransfer = 1033*VCLOCK_FRAME;
		    readout       = (VCLOCK_STORAGE*ybin + 536.*HCLOCK + (512./xbin+2)*video)*(1024./ybin);		
		    if(applicationTemplate.equals("Fullframe + clear")){
			clearTime    = (1033 + 1027)*VCLOCK_FRAME;
			cycleTime    = (INVERSION_DELAY + 100*expose + clearTime + frameTransfer + readout)/1.e6;
			exposureTime = expose/10000.;
		    }else{
			cycleTime    = (INVERSION_DELAY + 100*expose + frameTransfer + readout)/1.e6;
			exposureTime = cycleTime - frameTransfer/1.e6;
		    }
		    readout      /= 1.e6;
		    
		}else if(applicationTemplate.equals("Fullframe with overscan") || applicationTemplate.equals("Fullframe, overscan, no clear")){
		    
		    frameTransfer = 1033.*VCLOCK_FRAME;
		    readout       = (VCLOCK_STORAGE*ybin + 540.*HCLOCK + ((540./xbin)+2.)*video)*(1032/ybin);
		    if(applicationTemplate.equals("Fullframe with overscan")){
			clearTime     = (1033. + 1032.) * VCLOCK_FRAME;
			cycleTime     = (INVERSION_DELAY + 100*expose + clearTime + frameTransfer + readout)/1.e6;
			exposureTime  = expose/10000.;
		    }else{
			cycleTime     = (INVERSION_DELAY + 100*expose + frameTransfer + readout)/1.e6;
			exposureTime  = cycleTime - frameTransfer/1.e6;
		    }
		    readout      /= 1.e6;
		    
		}else if(applicationTemplate.equals("2 windows") || applicationTemplate.equals("4 windows") || 
			 applicationTemplate.equals("6 windows") || applicationTemplate.equals("2 windows + clear") ){
		    
		    if(applicationTemplate.equals("2 windows + clear") ){
			clearTime     = (1033 + 1027)*VCLOCK_FRAME;
		    }else{
			clearTime = 0.;
		    }
		    frameTransfer = 1033.*VCLOCK_FRAME;
		    cycleTime     = INVERSION_DELAY + 100*expose + frameTransfer + clearTime;
		    readout       = 0.;
		    
		    for(int i=0; i<numEnable; i++){
			
			int ystart = _windowPairs.getYstart(i);
			int xleft  = _windowPairs.getXleft(i);
			int xright = _windowPairs.getXright(i);
			int nx     = _windowPairs.getNx(i);
			int ny     = _windowPairs.getNy(i);
			
			int ystart_m = i > 0 ? _windowPairs.getYstart(i-1) : 1;
			int ny_m     = i > 0 ? _windowPairs.getNy(i-1)     : 0;
			
			// Time taken to shift the window next to the storage area
			double yShift = i > 0 ? (ystart-ystart_m-ny_m)*VCLOCK_STORAGE : (ystart-1)*VCLOCK_STORAGE;
			
			// Number of columns to shift whichever window is further from the edge of the readout
			// to get ready for simultaneous readout.
			int diffShift = Math.abs(xleft - 1 - (1024 - xright - nx + 1) );
			
			// Time taken to dump any pixels in a row that come after the ones we want.
			// The '8' is the number of HCLOCKs needed to open the serial register dump gates
			// If the left window is further from the left edge than the right window is from the
			// right edge, then the diffshift will move it to be the same as the right window, and
			// so we use the right window parameters to determine the number of hclocks needed, and
			// vice versa.
			int numHclocks   = (xleft - 1 > 1024-xright-nx+1) ?
			    nx + diffShift + (1024 - xright - nx + 1) + 8 :
			    nx + diffShift + (xleft - 1) + 8;
			
			// Time taken to read one line. The extra 2 is required to fill the video pipeline buffer
			double lineRead = VCLOCK_STORAGE*ybin + numHclocks*HCLOCK + (nx/xbin+2)*video;
			
			// Time taken to read window
			double read     = (ny/ybin)*lineRead;
			
			cycleTime += yShift + read;
			readout   += yShift + read;
		    }
		    
		    // Convert to microseconds
		    if(applicationTemplate.equals("2 windows + clear") ){
			exposureTime = expose/10000.;
		    }else{
			exposureTime = (cycleTime - frameTransfer)/1.e6;
		    }
		    
		    cycleTime   /= 1.e6;
		    readout     /= 1.e6;
		    
		}else if(applicationTemplate.equals("Drift mode")){		
		    
		    int ystart = _windowPairs.getYstart(0);
		    int xleft  = _windowPairs.getXleft(0);
		    int xright = _windowPairs.getXright(0);
		    int nx     = _windowPairs.getNx(0);
		    int ny     = _windowPairs.getNy(0);
		    
		    // Drift mode
		    nwins  = (int)(((1033. / ny ) + 1.)/2.);
		    pshift = (int)(1033.-(((2.*nwins)-1.)*ny));
		    
		    frameTransfer = (ny + ystart - 1.)*VCLOCK_FRAME;
		    int diffShift   = Math.abs(xleft - 1 - (1024-xright-nx+1));
		    int numHclocks  = (xleft - 1 > 1024-xright-nx+1) ?
			nx + diffShift + (1024-xright-nx+1) + 8 :
			nx + diffShift + (xleft-1) + 8;
		    double lineRead = VCLOCK_STORAGE*ybin + numHclocks*HCLOCK + (nx/xbin+2)*video;
		    double read     = (ny/ybin)*lineRead;
		    
		    cycleTime    = (INVERSION_DELAY + pshift*VCLOCK_STORAGE + 100*expose + frameTransfer + read)/1.e6;
		    exposureTime = cycleTime - frameTransfer/1.e6;
		    readout      = (read + pshift*VCLOCK_STORAGE)/1.e6;
		    
		}else if(applicationTemplate.equals("Timing test")){		
		    
		    // Same as drift mode except no compensating delays are added, so on average
		    // there is only one pipe shift per nwin frames
		    int ystart = _windowPairs.getYstart(0);
		    int xleft  = _windowPairs.getXleft(0);
		    int xright = _windowPairs.getXright(0);
		    int nx     = _windowPairs.getNx(0);
		    int ny     = _windowPairs.getNy(0);
		    
		    // Drift mode
		    nwins  = (int)(((1033. / ny ) + 1.)/2.);
		    pshift = (int)(1033.-(((2.*nwins)-1.)*ny));
		    
		    frameTransfer = (ny + ystart - 1.)*VCLOCK_FRAME;
		    int diffShift   = Math.abs(xleft - 1 - (1024-xright-nx+1));
		    int numHclocks  = (xleft - 1 > 1024-xright-nx+1) ?
			nx + diffShift + (1024-xright-nx+1) + 8 :
			nx + diffShift + (xleft-1) + 8;
		    double lineRead = VCLOCK_STORAGE*ybin + numHclocks*HCLOCK + (nx/xbin+2)*video;
		    double read     = (ny/ybin)*lineRead;
		    
		    cycleTime    = (INVERSION_DELAY + (pshift*VCLOCK_STORAGE)/nwins + 100*expose + frameTransfer + read)/1.e6;
		    exposureTime = cycleTime - frameTransfer/1.e6;
		    readout      = (read + (pshift*VCLOCK_STORAGE)/nwins)/1.e6;
		    
		}else{
		    throw new Error("Application = \"" + applicationTemplate + "\" is unrecognised. Programming error in speed");
		}
		deadTime  = cycleTime - exposureTime;
		frameRate = 1./cycleTime;
		
		if(method == CYCLE_TIME_ONLY)
		    return cycleTime;
		
		// Signal-to-noise info. Not a disaster if we fail to compute this, so
		// make sure that we can recover from failures with a try block
		final double AP_SCALE = 1.5;
		double zero = 0., sky = 0., skyTot = 0., gain = 0., read = 0., darkTot = 0.;
		double total = 0., peak = 0., correct = 0., signal = 0., readTot = 0., seeing = 0.;
		double noise = 1., skyPerPixel = 0., narcsec = 0., npix = 0., signalToNoise = 0.;
		try {
		    
		    // Get the parameters for magnitudes
		    zero    = _telescope.zeroPoint[_filterIndex];
		    double mag     = _magnitudeText.getValue();
		    seeing  = _seeingText.getValue();
		    sky     = SKY_BRIGHT[_skyBrightIndex][_filterIndex];
		    double airmass = _airmassText.getValue();
		    if(readSpeed.equals("Fast")){
			gain = GAIN_FAST;
		    }else if(readSpeed.equals("Turbo")){
			gain = GAIN_TURBO;
		    }else if(readSpeed.equals("Slow")){
			gain = GAIN_SLOW;
		    }
		    int binIndex;
		    switch(Math.max(xbin,ybin)){
			case 1:
			    binIndex = 0;
			    break;
			case 2:
			case 3:
			    binIndex = 1;
			    break;
			case 4:
			case 5:
			case 6:
			    binIndex = 2;
			    break;
			default:
			    binIndex = 3;
		    }
		    if(readSpeed.equals("Fast")){
			read = READ_NOISE_FAST[binIndex];
		    }else if(readSpeed.equals("Turbo")){
			read = READ_NOISE_TURBO[binIndex];
		    }else if(readSpeed.equals("Slow")){
			read = READ_NOISE_SLOW[binIndex];
		    }
		    
		    double plateScale = _telescope.plateScale;
		    
		    // Now calculate expected counts
		    total = Math.pow(10.,(zero-mag-airmass*EXTINCTION[_filterIndex])/2.5)*exposureTime;
		    peak  = total*xbin*ybin*Math.pow(plateScale/(seeing/2.3548),2)/(2.*Math.PI);
		    
		    // Work out fraction of flux in aperture with radius AP_SCALE*seeing
		    correct      = 1. - Math.exp(-Math.pow(2.3548*AP_SCALE, 2)/2.);
		    
		    double skyPerArcsec = Math.pow(10.,(zero-sky)/2.5)*exposureTime;
		    skyPerPixel = skyPerArcsec*Math.pow(plateScale,2)*xbin*ybin;
		    narcsec     = Math.PI*Math.pow(AP_SCALE*seeing,2);
		    skyTot      = skyPerArcsec*narcsec;
		    npix        = Math.PI*Math.pow(AP_SCALE*seeing/plateScale,2)/xbin/ybin;
		    signal      = correct*total;
		    darkTot     = npix*DARK_COUNT*exposureTime;
		    readTot     = npix*Math.pow(read, 2)/gain;
		    noise       = Math.sqrt( (readTot + darkTot + skyTot + signal) / gain);
		    
		    // Now compute signal-to-noise in 3 hour seconds run
		    signalToNoise = signal/noise*Math.sqrt(3*3600./cycleTime);
		    
		    _totalCounts.setText(round(total,1));
		    
		    peak = (int)(100.*peak+0.5)/100.;
		    _peakCounts.setText(round(peak,2));
		    if(peak > 60000){
			_peakCounts.setBackground(ERROR_COLOUR);
		    }else if(peak > 25000){
			_peakCounts.setBackground(WARNING_COLOUR);
		    }else{
			_peakCounts.setBackground(DEFAULT_COLOUR);
		    }
		    
		    _signalToNoise.setText(round(signalToNoise,1));
		    _signalToNoiseOne.setText(round(signal/noise,2));
		    
		    _magInfo = true;
		    
		}
		catch(Exception e){
		    _totalCounts.setText("");
		    _peakCounts.setText("");
		    if(_magInfo)
			System.out.println(e.toString());
		    _magInfo = false;
		}
		
		double dutyCycle = 100.*exposureTime/cycleTime;
		frameTransfer   /= 1.e6;
		
		// Update standard timing data fields
		_frameRate.setText(round(frameRate,3));
		_cycleTime.setText(round(cycleTime,4));
		_dutyCycle.setText(round(dutyCycle,2));
		
		if(method == DETAILED_TIMING){
		    
		    String pipeShift = (applicationTemplate.equals("Drift mode") || applicationTemplate.equals("Timing test")) ?
			String.valueOf(pshift) : new String("UNDEFINED");
		    
		    String nWindows = (applicationTemplate.equals("Drift mode") || applicationTemplate.equals("Timing test")) ?
			String.valueOf(nwins) : new String("UNDEFINED");
		    
		    Object[][] data = {
			{"Frame rate",       "=", round(frameRate,3),     "Hz"},
			{"Cycle time",       "=", round(cycleTime,4),     "sec"},
			{"Exposure time",    "=", round(exposureTime,4),  "sec"},
			{"Dead time",        "=", round(deadTime,4),      "sec"},
			{"Readout time",     "=", round(readout,4),       "sec"},
			{"Frame transfer",   "=", round(frameTransfer,4), "sec"},
			{"Duty cycle",       "=", round(dutyCycle,2),     "%"},
			{"Pipe shift",       "=", pipeShift,              "pixels"},
			{"nwin",             "=", nWindows,               "windows"},
			{"Zeropoint",        "=", round(zero,2),          "mags"},
			{"Read noise",       "=", round(read,2),          "counts RMS"},
			{"Gain",             "=", round(gain,2),           "electrons/count"},
			{"Aperture diameter","=", round(2.*AP_SCALE*seeing,1), "arcseconds"},
			{"Aperture area",    "=", round(npix,1),          "binned pixels"},
			{"Signal",           "=", round(total,1),         "total counts"},
			{"Signal",           "=", round(signal,1),        "counts in aperture"},
			{"Sky background",   "=", round(sky,2),           "mags/arcsec**2"},
			{"Sky background",   "=", round(skyPerPixel,2),   "counts/binned pixel"},
			{"Sky background",   "=", round(skyTot,1),        "counts in aperture"},
			{"Dark",             "=", round(darkTot,1),       "counts in aperture"},
			{"Read noise",       "=", round(readTot,0),       "effective counts in aperture"},
			{"Signal-to-noise",  "=", round(signal/noise,2),  "in single exposure"},
			{"Signal-to-noise",  "=", round(signalToNoise,1), "in 3 hour run"},
		    };
		    
		    JTable table = new JTable(new TableModel(data));
		    table.setGridColor(DEFAULT_COLOUR);
		    table.getColumnModel().getColumn(0).setPreferredWidth(120);
		    table.getColumnModel().getColumn(1).setPreferredWidth(10);
		    table.getColumnModel().getColumn(2).setPreferredWidth(75);
		    table.getColumnModel().getColumn(3).setPreferredWidth(180);
		    
		    JOptionPane.showMessageDialog(this, table, "Timing details", JOptionPane.INFORMATION_MESSAGE);
		    
		    return cycleTime;
		}
		
	    }else{
		_frameRate.setText("UNDEFINED");
		_cycleTime.setText("UNDEFINED");
		_dutyCycle.setText("UNDEFINED");
	    }
	}
	catch(Exception e){
	    _frameRate.setText("UNDEFINED");
	    _cycleTime.setText("UNDEFINED");
	    _dutyCycle.setText("UNDEFINED");
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	}
	return 0.;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Computes the number of bytes per image as needed to estimate the disk space used. */
    public int nbytesPerImage(){

	try{

	    if(isValid(_validStatus)){

		// time bytes
		int n = 24;

		if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){

		    n += 12*(512/xbin)*(1024/ybin);
		    
		}else if(applicationTemplate.equals("Fullframe with overscan") || applicationTemplate.equals("Fullframe, overscan, no clear")){

		    n += 12*(540/xbin)*(1032/ybin);
		    
		}else{
		    
		    for(int i=0; i<numEnable; i++)
			n += 12*(_windowPairs.getNx(i) / xbin ) * (_windowPairs.getNy(i) / ybin );

		}
		
		return n;
		    
	    }
	}
	catch(Exception e){
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	}
	return 1;
    }


    //------------------------------------------------------------------------------------------------------------------------------------------

    /** This routine sets up a server for rtplot so that rtplot can grab the current window values.
     *  It attempts to sends the current values over whether they are OK or not, and leaves rtplot to check them.
     *  Note that the port number used here must match the one used in rtplot.
     */
    public void runRtplotServer() {

	try {

	    ServerSocket ss = new ServerSocket(5100);
	    
	    for(;;){
		Socket     client = ss.accept();
		BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		PrintWriter   out = new PrintWriter(client.getOutputStream());
		
		out.print("HTTP/1.0 200 OK\r\n");
		out.print("Content-Type: text/plain\r\n");

		// OK, now we send the windows information if we can get it	
		try {

		    // Get everything first before attempting to respond
		    xbin = xbinText.getValue();
		    ybin = ybinText.getValue();	
		    setNumEnable();
		    String binFactors  = new String(xbin + " " + ybin + " " + Math.max(2, 2*numEnable) + "\r\n");
		    int content_length = binFactors.length();
		    String[] window    = new String[Math.max(2,2*numEnable)];
		    if(numEnable > 0){

			int xleft, xright, ystart, nx, ny;
			for(int i=0; i<numEnable; i++){
			    xleft  = _windowPairs.getXleft(i);
			    xright = _windowPairs.getXright(i);
			    ystart = _windowPairs.getYstart(i);
			    nx     = _windowPairs.getNx(i);
			    ny     = _windowPairs.getNy(i);
			    window[2*i]     = new String(xleft + " "  + ystart + " " + nx + " " + ny + "\r\n");
			    content_length += window[2*i].length();
			    window[2*i+1]   = new String(xright + " " + ystart + " " + nx + " " + ny + "\r\n");
			    content_length += window[2*i+1].length();
			}

		    }else if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){
			window[0] = new String("1   1 512 1024\r\n");
			content_length += window[0].length();
			window[1] = new String("513 1 512 1024\r\n");
			content_length += window[1].length();
		    }else if(applicationTemplate.equals("Fullframe with overscan") || applicationTemplate.equals("Fullframe, overscan, no clear")){
			window[0] = new String("1   1 520 1032\r\n");
			content_length += window[0].length();
			window[1] = new String("513 1 520 1032\r\n");
			content_length += window[1].length();
		    }
		    out.print("Content-Length: " + content_length + "\r\n\r\n");

		    // Now the content
		    out.print(binFactors);
		    for(int i=0; i<Math.max(2,2*numEnable); i++)
			out.print(window[i]);

		    if(DEBUG) System.out.println("Have just responded to a request from rtplot");
		}
		catch(Exception e){
		    out.print("Content-Length: 26\r\n\r\n");
		    out.print("No valid data available\r\n");
		    System.out.println(e);
		    System.out.println("Failed to respond to a request from rtplot");
		    _showExceptionDialog(e);
		}
		out.close();
		in.close();
		client.close();
	    }	    
	}
	catch(Exception e) {
	    if(DEBUG) e.printStackTrace();
	    System.err.println(e);
	    _showExceptionDialog(e);
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Main program. Calls constructor and starts rtplot server */

    public static void main(String[] args) {
	Udriver cw = new Udriver();
	if(RTPLOT_SERVER_ON){
	    logPanel.add("Starting rtplot server", LogPanel.WARNING, false);
	    cw.runRtplotServer();
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /* Choose an XML file name for saving an application */
    private boolean _chooseSaveApp() {
	try {
	    int result = _xmlFileChooser.showSaveDialog(null);
	    if(result == JFileChooser.APPROVE_OPTION){
		_xmlFile = _xmlFileChooser.getSelectedFile();
		if (_xmlFile.getPath().indexOf(".xml") != _xmlFile.getPath().length() - 4 ){
		    String newFilePath = _xmlFile.getPath() + ".xml";
		    _xmlFile = new File(newFilePath);
		}
		return true;
	    }else{
		throw new Exception("No XML file name chosen for saving application");
	    }
	}
	catch(Exception e){
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    return false;
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /* Choose an XML file name for loading an application */
    private boolean _chooseLoadApp() {
	try {
	    int result = _xmlFileChooser.showOpenDialog(null);
	    if(result == JFileChooser.APPROVE_OPTION){
		_xmlFile = _xmlFileChooser.getSelectedFile();
		return true;
	    }else{
		throw new Exception("No XML file name chosen for loading application");
	    }
	}
	catch(Exception e){
	    logPanel.add(e.toString(), LogPanel.ERROR, false);
	    return false;
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Method for adding components to GridBagLayout for the window panel
    private static void addComponent (Container cont, Component comp, int gridx, int gridy, 
				      int gridwidth, int gridheight, int fill, int anchor){

	GridBagConstraints gbc = new GridBagConstraints ();
	gbc.gridx      = gridx;
	gbc.gridy      = gridy;
	gbc.gridwidth  = gridwidth;
	gbc.gridheight = gridheight;
	gbc.fill       = fill;
	gbc.anchor     = anchor;
	gbLayout.setConstraints(comp, gbc);
	cont.add (comp);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Method for adding components to GridBagLayout for the action panel
    private static void addActionComponent (Container cont, Component comp, int gridx, int gridy){

	GridBagConstraints gbc = new GridBagConstraints ();
	gbc.gridx      = gridx;
	gbc.gridy      = gridy;
	gbc.gridwidth  = 1;
	gbc.gridheight = 1;
	gbc.insets     = new Insets(0,5,0,5);
	gbc.fill       = GridBagConstraints.HORIZONTAL;
	gbc.anchor     = GridBagConstraints.CENTER;
	gbLayout.setConstraints(comp, gbc);
	cont.add (comp);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Splits up multiple arguments from configuration file */
    private String[] _loadSplitProperty(Properties properties, String key) throws Exception {
	String propString = _loadProperty(properties, key);
	StringTokenizer stringTokenizer = new StringTokenizer(propString, ";\n");
	String[] multiString = new String[stringTokenizer.countTokens()];
	int i = 0;
	while(stringTokenizer.hasMoreTokens())
	    multiString[i++] = stringTokenizer.nextToken().trim();
	return multiString;
    }

    private String _loadProperty(Properties properties, String key) throws Exception {
	String value = properties.getProperty(key);
	if(value == null)
	    throw new Exception("Could not find " + key + " in configration file " + CONFIG_FILE);
	return value;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Checks that a property has value YES or NO and returns true if yes. It throws an exception
     * if it neither yes nor no
     */
    private boolean _loadBooleanProperty(Properties properties, String key) throws Exception {
	String value = properties.getProperty(key);
	if(value == null)
	    throw new Exception("Could not find " + key + " in configration file " + CONFIG_FILE);

	if(value.equalsIgnoreCase("YES") || value.equalsIgnoreCase("TRUE")){
	    return true;
	}else if(value.equalsIgnoreCase("NO") || value.equalsIgnoreCase("FALSE")){
	    return false;
	}else{
	    throw new Exception("Key " + key + " has value = " + value + " which does not match yes/no/true/false");
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Handles display of exception messages which require acknowledgement from user */
    private void _showExceptionDialog(Exception e) {
	JOptionPane.showMessageDialog(this, "" + e, e.getMessage(), JOptionPane.ERROR_MESSAGE);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Member class for checking whether setup has changed. This is needed to see whether
     *  the user should be prompted to confirm the target name after a format change */
    private class CheckFormat extends Settings {

	private String target;
	private String filters;

	// Constructor, stores current values, with no attempt at
	// checking validity.

	public CheckFormat() {

	    super();
	    target  = _objectText.getText();
	    //filters = _filterText.getText();

	}

	// Check whether there has been a change of format without the
	// target name changing or if the target name is blank
	public boolean hasChanged() {

	    if(!target.equals(_objectText.getText())){
		update();
		return false;
	    }
	    //if(!filters.equals(_filterText.getText())) return true;

	    return super.hasChanged();
	}

	// Updates the stored format
	public void update() {
	    super.update();
	    target  = _objectText.getText();
	    //filters = _filterText.getText();
	}

    }    

    /** Member class for storage of the settings that can make a difference
     * to the data **/
    private class Settings {

	private int    xbin;
	private int    ybin;
	private int    nblue;
	private int    expose;
	private int    numEnable;
	private String readSpeed;
	private int    nTemplate;
	private int[]  ystart = new int [3];
	private int[]  xleft  = new int [3];
	private int[]  xright = new int [3];
	private int[]  nx     = new int [3];
	private int[]  ny     = new int [3];

	// Constructor, stores current values, with no attempt at
	// checking validity.

	public Settings() {
	    update();
	}

	// Check whether there has been a change of format without the
	// target name changing or if the target name is blank
	public boolean hasChanged() {

	    if(xbin      != getCurrentXbin())          return true;
	    if(ybin      != getCurrentYbin())          return true;
	    if(nblue     != getCurrentNblue())         return true;
	    if(expose    != getCurrentExpose())        return true;
	    if(numEnable != getCurrentNumEnable())     return true;
	    for(int i=0; i<numEnable; i++){
		if(ystart[i] != getCurrentYstart(i))   return true;
		if(xleft[i]  != getCurrentXleft(i))    return true;
		if(xright[i] != getCurrentXright(i))   return true;
		if(nx[i]     != getCurrentNx(i))       return true;
		if(ny[i]     != getCurrentNy(i))       return true;
	    }
	    if(nTemplate != _whichTemplate()) return true;
	    if(!readSpeed.equals((String) speedChoice.getSelectedItem())) return true;

	    // All tests passed, there has been no change
	    return false;
	}

	// Updates the stored format
	public void update() {

	    // Get current values
	    xbin      = getCurrentXbin();
	    ybin      = getCurrentYbin();
	    nblue     = getCurrentNblue();
	    expose    = getCurrentExpose();
	    numEnable = getCurrentNumEnable();
	    for(int i=0; i<numEnable; i++){
		ystart[i] = getCurrentYstart(i);
		xleft[i]  = getCurrentXleft(i);
		xright[i] = getCurrentXright(i);
		nx[i]     = getCurrentNx(i);
		ny[i]     = getCurrentNy(i);
	    }
	    nTemplate  = _whichTemplate();
	    readSpeed  = (String) speedChoice.getSelectedItem();
	}

	// Series of routines for getting current settings
	public int getCurrentXbin(){
	    try{
		return xbinText.getValue();
	    } 
	    catch(Exception e){
		return 1;
	    }
	}

	public int getCurrentYbin(){
	    try{
		return ybinText.getValue();
	    } 
	    catch(Exception e){
		return 1;
	    }
	}

	public int getCurrentNblue(){
	    try{
		return nblueText.getValue();
	    } 
	    catch(Exception e){
		return 1;
	    }
	}

	public int getCurrentExpose(){
	    try{
		return _getExpose();
	    } 
	    catch(Exception e){
		return 5;
	    }
	}

	public int getCurrentNumEnable(){
	    try{
		return Integer.parseInt(TEMPLATE_PAIR[_whichTemplate()]);
	    }
	    catch(Exception e){
		return 1;
	    }
	}

	public int getCurrentYstart(int nwin){
	    try{
		return _windowPairs.getYstart(nwin);
	    }
	    catch(Exception e){
		return 1;
	    }
	}

	public int getCurrentXleft(int nwin){
	    try{
		return _windowPairs.getXleft(nwin);
	    }
	    catch(Exception e){
		return 1;
	    }
	}

	public int getCurrentXright(int nwin){
	    try{
		return _windowPairs.getXright(nwin);
	    }
	    catch(Exception e){
		return 513;
	    }
	}

	public int getCurrentNx(int nwin){
	    try{
		return _windowPairs.getNx(nwin);
	    }
	    catch(Exception e){
		return 1;
	    }
	}

	public int getCurrentNy(int nwin){
	    try{
		return _windowPairs.getNy(nwin);
	    }
	    catch(Exception e){
		return 1;
	    }
	}

    }    

    //------------------------------------------------------------------------------------------------------------------------------------------
    //
    // Disable all settings buttons. This is in order to prevent the user changing a setup that has not been saved

    private void _disableAll() {
	loadApp.setEnabled(false);
	_windowPairs.setNpair(0);
	templateChoice.setEnabled(false);
	speedChoice.setEnabled(false);
	exposeText.setEnabled(false);
	tinyExposeText.setEnabled(false);
	numExposeText.setEnabled(false);
	xbinText.setEnabled(false);
	ybinText.setEnabled(false);
	nblueText.setEnabled(false);
	if(OBSERVING_MODE){
	    _objectText.setEnabled(false);
	   // _filterText.setEnabled(false);
	    _progidText.setEnabled(false);
	    _piText.setEnabled(false);
	    _observerText.setEnabled(false);
	}
	logPanel.add("The window settings now disabled. You must save them to disk before you can change them.", LogPanel.WARNING, false);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
    //
    // Enable all settings buttons.

    private void _enableAll() {
	enableChanges.setEnabled(EXPERT_MODE || _unsavedSettings);
	loadApp.setEnabled(true);
	_windowPairs.setNpair(numEnable);
	templateChoice.setEnabled(true);
	speedChoice.setEnabled(true);
	exposeText.setEnabled(true);
	tinyExposeText.setEnabled(true);
	numExposeText.setEnabled(true);
	xbinText.setEnabled(true);
	ybinText.setEnabled(true);
	nblueText.setEnabled(true);
	if(OBSERVING_MODE){
		if (_runType == "data" || _runType == "technical") {
			_objectText.setEnabled(true);
			_progidText.setEnabled(true);
			_piText.setEnabled(true);
		}
		_filter1.setEnabled(true);
		_filter2.setEnabled(true);
		_filter3.setEnabled(true);
	    _observerText.setEnabled(true);
	}
    }

    // Modifies window locations so that a full frame NxM binned window can
    // be used as a bias. Does so by ensuring no gap in the middle of the CCDs
    private boolean _syncWindows() {
	if(isValid(true)){
	    try {
		if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){
		    
		    if(512 % xbin != 0 || 1024 % ybin != 0) 
			throw new Exception("Cannot synchronise fullframe with current binning factors. " +
					    "xbin must divide into 512, ybin must divide into 1024");
		    
		}else if(applicationTemplate.equals("Fullframe with overscan") || applicationTemplate.equals("Fullframe, overscan, no clear")){
		    
		    if(540 % xbin != 0 || 1032 % ybin != 0) 
			throw new Exception("Cannot synchronise fullframe+overscan with current binning factors. " +
					    "xbin must divide into 540, ybin must divide into 1032");
		}else{ 
		    
		    for(int i=0; i<numEnable; i++){
			_windowPairs.setYstartText(i, Integer.toString(_syncStart(_windowPairs.getYstart(i), ybin, 1,   1024, 512)) );
			_windowPairs.setXleftText(i,  Integer.toString(_syncStart(_windowPairs.getXleft(i),  xbin, 1,   512, 512)) );
			_windowPairs.setXrightText(i, Integer.toString(_syncStart(_windowPairs.getXright(i), xbin, 513, 1024, 512)) );
		    }

		}
		return true;
	    }
	    catch(Exception e){
		logPanel.add(e.toString(), LogPanel.ERROR, false);
		return false;
	    }
	}
	return true;
    }

    // Synchronises window so that the binned pixels end at ref and start at ref+1
    private int _syncStart(int start, int bin, int min, int max, int ref){
	int n = Math.round((float)((ref+1-start))/bin);
	start = ref + 1 - bin*n;
	if(start < min) start += bin;
	if(start > max) start -= bin;
	return start;
    }

    // Checks whether windows are synchronised
    private boolean _areSynchronised(){
	if(isValid(false)){
	    try{ 
		if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){
		    
		    if(512 % xbin != 0 || 1024 % ybin != 0) return false;
		    
		}else if(applicationTemplate.equals("Fullframe with overscan") || applicationTemplate.equals("Fullframe, overscan, no clear")){
		    
		    if(540 % xbin != 0 || 1032 % ybin != 0) return false;

		}else{

		    for(int i=0; i<numEnable; i++){
			if((513 - _windowPairs.getYstart(i)) % ybin != 0) return false;
			if((513 - _windowPairs.getXleft(i))  % xbin != 0) return false;
			if((513 - _windowPairs.getXright(i)) % xbin != 0) return false;
		    }

		}
		return true;
	    }
	    catch(Exception e){
		logPanel.add(e.toString(), LogPanel.ERROR, false);
		return false;
	    }
	}
	return true;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** This class is for the display of the detailed timing information in 'speed' */
    class TableModel extends AbstractTableModel {
	
	private Object[][] data;

	public TableModel(Object[][] data){
	    this.data = data;
	}
			
	public int getColumnCount() {
	    return data[0].length;
	}
		    
	public int getRowCount() {
	    return data.length;
	}

	public Object getValueAt(int row, int col) {
	    return data[row][col];
	}

    }

    // Converts a double to a string rounding to specified number of decimals
    public String round(double f, int ndp){
	final DecimalFormat form = new DecimalFormat();
	form.setMaximumFractionDigits(ndp);
	return form.format(f);
    }

    //--------------------------------------------------------------------------------------------------------------
    // Load the configuration file

    public void loadConfig() throws Exception {

	Properties properties = new Properties();
	properties.load(new FileInputStream(CONFIG_FILE));

	RTPLOT_SERVER_ON     = _loadBooleanProperty(properties, "RTPLOT_SERVER_ON");
	FILE_LOGGING_ON      = _loadBooleanProperty(properties, "FILE_LOGGING_ON");
	ULTRACAM_SERVERS_ON  = _loadBooleanProperty(properties, "ULTRACAM_SERVERS_ON");
	OBSERVING_MODE       = _loadBooleanProperty(properties, "OBSERVING_MODE");
	DEBUG                = _loadBooleanProperty(properties, "DEBUG");
	TELESCOPE            = _loadProperty(properties,        "TELESCOPE");
	UAC_DATABASE_HOST	 = _loadProperty(properties, "UAC_DATABASE_HOST");

	// Set the current telescope 
	for(int i=0; i<TELESCOPE_DATA.length; i++){
	    if(TELESCOPE_DATA[i].name.equals(TELESCOPE)){
		_telescope = TELESCOPE_DATA[i];
		break;
	    }
	}

	if(_telescope == null){
	    String MESSAGE = "TELESCOPE = " + TELESCOPE + " was not found amongst the list of supported telescopes:\n";
	    for(int i=0; i<TELESCOPE_DATA.length-1; i++)
		MESSAGE += TELESCOPE_DATA[i].name + ", ";
	    MESSAGE += TELESCOPE_DATA[TELESCOPE_DATA.length-1].name;
	    throw new Exception(MESSAGE);
	}

	HTTP_CAMERA_SERVER   = _loadProperty(properties, "HTTP_CAMERA_SERVER");
	if(!HTTP_CAMERA_SERVER.trim().endsWith("/"))
	    HTTP_CAMERA_SERVER = HTTP_CAMERA_SERVER.trim() + "/";
	
	HTTP_DATA_SERVER    = _loadProperty(properties, "HTTP_DATA_SERVER");
	if(!HTTP_DATA_SERVER.trim().endsWith("/"))
	    HTTP_DATA_SERVER = HTTP_DATA_SERVER.trim() + "/";
	
	HTTP_PATH_GET         = _loadProperty(properties,        "HTTP_PATH_GET");
	HTTP_PATH_EXEC        = _loadProperty(properties,        "HTTP_PATH_EXEC");
	HTTP_PATH_CONFIG      = _loadProperty(properties,        "HTTP_PATH_CONFIG");
	HTTP_SEARCH_ATTR_NAME = _loadProperty(properties,        "HTTP_SEARCH_ATTR_NAME");
	APP_DIRECTORY         = _loadProperty(properties,        "APP_DIRECTORY");
	XML_TREE_VIEW         = _loadBooleanProperty(properties, "XML_TREE_VIEW");
	
	TEMPLATE_FROM_SERVER  = OBSERVING_MODE && _loadBooleanProperty(properties, "TEMPLATE_FROM_SERVER");
	String dsep = System.getProperty("file.separator");
	
	TEMPLATE_DIRECTORY   = _loadProperty(properties, "TEMPLATE_DIRECTORY");
	if(!TEMPLATE_DIRECTORY.trim().endsWith(dsep))
	    TEMPLATE_DIRECTORY = TEMPLATE_DIRECTORY.trim() + dsep;
	
	EXPERT_MODE        = _loadBooleanProperty(properties, "EXPERT_MODE");
	LOG_FILE_DIRECTORY = _loadProperty(properties, "LOG_FILE_DIRECTORY");
	CONFIRM_ON_CHANGE  =  OBSERVING_MODE && _loadBooleanProperty(properties, "CONFIRM_ON_CHANGE");
	CHECK_FOR_MASK     =  OBSERVING_MODE && _loadBooleanProperty(properties, "CHECK_FOR_MASK");


	TEMPLATE_LABEL     = _loadSplitProperty(properties, "TEMPLATE_LABEL");
	
	TEMPLATE_PAIR      = _loadSplitProperty(properties, "TEMPLATE_PAIR");
	if(TEMPLATE_PAIR.length != TEMPLATE_LABEL.length)
	    throw new Exception("Number of TEMPLATE_PAIR = " + TEMPLATE_PAIR.length + 
				" does not equal the number of TEMPLATE_LABEL = " + TEMPLATE_LABEL.length);
	
	TEMPLATE_APP       = _loadSplitProperty(properties, "TEMPLATE_APP");
	if(TEMPLATE_APP.length != TEMPLATE_LABEL.length)
	    throw new Exception("Number of TEMPLATE_APP = " + TEMPLATE_APP.length + 
				" does not equal the number of TEMPLATE_LABEL = " + TEMPLATE_LABEL.length);
	
	TEMPLATE_ID        = _loadSplitProperty(properties, "TEMPLATE_ID");
	if(TEMPLATE_ID.length != TEMPLATE_LABEL.length)
	    throw new Exception("Number of TEMPLATE_ID = " + TEMPLATE_ID.length + 
				" does not equal the number of TEMPLATE_LABEL = " + TEMPLATE_LABEL.length);
	
	POWER_ON  = _loadProperty(properties, "POWER_ON");

	POWER_OFF = _loadProperty(properties, "POWER_OFF");
	
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Create the "File" menu
    private JMenu createFileMenu() {
	
	JMenu fileMenu = new JMenu("File");
	
	// Add actions to the "File" menu
	
	// Allow user to save a standard rtplot windows file
	JMenuItem _rtplotSave = new JMenuItem("Save rtplot file");
	_rtplotSave.addActionListener(
				      new ActionListener(){
					  public void actionPerformed(ActionEvent e){
					      int result = _rtplotFileChooser.showSaveDialog(null);
					      if(result == JFileChooser.APPROVE_OPTION){
						  _rtplotFile = _rtplotFileChooser.getSelectedFile();
						  if (_rtplotFile.getPath().indexOf(".dat") != _rtplotFile.getPath().length() - 4 ){
						      String newFilePath = _rtplotFile.getPath() + ".dat";
						      _rtplotFile = new File(newFilePath);
						  }
						  saveToRtplot();
					      }else{
						  System.out.println("No rtplot file chosen.");
					      }
					  }
				      });
	
	// Quit the program
	JMenuItem _quit = new JMenuItem("Quit");
	_quit.addActionListener(
				new ActionListener(){
				    public void actionPerformed(ActionEvent e){
					if(logPanel.loggingEnabled())
					    logPanel.stopLog();
					System.exit(0);
				    }
				});
	
	fileMenu.add(_rtplotSave);
	fileMenu.add(_quit);
	return fileMenu;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Create the "Settings" menu
    private JMenu createSettingsMenu() throws Exception {
	
	JMenu settingsMenu = new JMenu("Settings");
	
	if(OBSERVING_MODE){
	    
	    _setExpert = new JCheckBoxMenuItem("Expert mode");
	    _setExpert.setState(EXPERT_MODE);
	    _setExpert.addActionListener(
					 new ActionListener(){
					     public void actionPerformed(ActionEvent e){
						 EXPERT_MODE = _setExpert.getState();
						 if(OBSERVING_MODE){
						     int selIndex = _actionPanel.getSelectedIndex();
						     _actionPanel.removeTabAt(0);
						     if(EXPERT_MODE)							     
							 _actionPanel.insertTab("Setup", null, _expertSetupPanel, null, 0);
						     else
							 _actionPanel.insertTab("Setup", null, _noddySetupPanel, null, 0);
						     _actionPanel.setSelectedIndex(selIndex);
						 }
						 if(EXPERT_MODE){
						     _enableAll();
						 }else{
						     if(_unsavedSettings) _disableAll();
						     tinyExposeText.setText("5");
						 }
						 _setEnabledActions();
						 updateGUI();
					     }
					 });
	    
	    _templatesFromServer = new JCheckBoxMenuItem("Templates from server");
	    _templatesFromServer.setState(TEMPLATE_FROM_SERVER);
	    _templatesFromServer.addActionListener(
						   new ActionListener(){
						       public void actionPerformed(ActionEvent e){
							   Udriver.TEMPLATE_FROM_SERVER = _templatesFromServer.getState();
						       }
						   });
	    
	    _ucamServersOn = new JCheckBoxMenuItem("ULTRACAM servers on");
	    _ucamServersOn.setState(ULTRACAM_SERVERS_ON);
	    _ucamServersOn.addActionListener(
					     new ActionListener(){
						 public void actionPerformed(ActionEvent e){
						     Udriver.ULTRACAM_SERVERS_ON = _ucamServersOn.getState();
						     if(Udriver.ULTRACAM_SERVERS_ON)
							 getRunNumber();
						     _setEnabledActions();
						 }
					     });
	    
	    _fileLogging = new JCheckBoxMenuItem("File logging");
	    _fileLogging.setState(logPanel.loggingEnabled());
	    _fileLogging.addActionListener(
					   new ActionListener(){
					       public void actionPerformed(ActionEvent e){
						   if(_fileLogging.getState())
						       logPanel.startLog();
						   else
						       logPanel.stopLog();
					       }
					   });
	    
	    _responseAsText = new JCheckBoxMenuItem("Show responses as text");
	    _responseAsText.setState(true);
	    _responseAsText.addActionListener(
					      new ActionListener(){
						  public void actionPerformed(ActionEvent e){
						      _replyPanel.setTreeView(!_responseAsText.getState());
						  }
					      });
	    
	    _confirmOnChange = new JCheckBoxMenuItem("Confirm target name");
	    _confirmOnChange.setState(CONFIRM_ON_CHANGE);
	    _confirmOnChange.addActionListener(
					       new ActionListener(){
						   public void actionPerformed(ActionEvent e){
						       Udriver.CONFIRM_ON_CHANGE = _confirmOnChange.getState();
						   }
					       });

	    _dataFromImedia1 = new JCheckBoxMenuItem("Retrieve Temp/Slide data from IMEDIA1");
	    _dataFromImedia1.setState(DATA_FROM_IMEDIA1);
	    _dataFromImedia1.addActionListener(
					       new ActionListener(){
						   public void actionPerformed(ActionEvent e){
						       Udriver.DATA_FROM_IMEDIA1 = _dataFromImedia1.getState();
						   }
					       });

	    _checkForMask = new JCheckBoxMenuItem("Check for mask");
	    _checkForMask.setState(CHECK_FOR_MASK);
	    _checkForMask.addActionListener(
		new ActionListener(){
		    public void actionPerformed(ActionEvent e){
			Udriver.CHECK_FOR_MASK = _checkForMask.getState();
		    }
		});

		_useUACdb = new JCheckBoxMenuItem("Use UAC db for lookup");
		_useUACdb.setState(USE_UAC_DB);
		_useUACdb.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e){
						Udriver.USE_UAC_DB = _useUACdb.getState();
					}
				});

	    // Add actions to the "Settings" menu	
	    settingsMenu.add(_setExpert);
	    settingsMenu.add(_templatesFromServer);
	    settingsMenu.add(_ucamServersOn);
	    settingsMenu.add(_fileLogging);
	    settingsMenu.add(_responseAsText);
	    settingsMenu.add(_confirmOnChange);
	    settingsMenu.add(_dataFromImedia1);
	    settingsMenu.add(_checkForMask);
	    settingsMenu.addSeparator();
		settingsMenu.add(_useUACdb);
	    settingsMenu.addSeparator();
	}
	
	// Telescope choices
	JRadioButtonMenuItem[] telescopeMenuItem = new JRadioButtonMenuItem[TELESCOPE_DATA.length];
	ButtonGroup telescopeGroup = new ButtonGroup();
	for(int ntel=0; ntel<TELESCOPE_DATA.length; ntel++){
	    telescopeMenuItem[ntel] = new JRadioButtonMenuItem(TELESCOPE_DATA[ntel].name);
	    
	    telescopeMenuItem[ntel].addActionListener(
						      new ActionListener(){
							  public void actionPerformed(ActionEvent e){
							      TELESCOPE = ((JRadioButtonMenuItem)e.getSource()).getText();
							      // Set the current telescope 
							      for(int i=0; i<TELESCOPE_DATA.length; i++){
								  if(TELESCOPE_DATA[i].name.equals(TELESCOPE)){
								      _telescope = TELESCOPE_DATA[i];
								      break;
								  }
							      }
							  }});
	    telescopeGroup.add(telescopeMenuItem[ntel]);
	    settingsMenu.add(telescopeMenuItem[ntel]);
	}

	// Select the current telescope 
	for(int i=0; i<TELESCOPE_DATA.length; i++){
	    if(TELESCOPE_DATA[i].name.equals(TELESCOPE)){
		telescopeMenuItem[i].setSelected(true);
		break;
	    }
	}
	
	return settingsMenu;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Creates the actions panel to send commands to the servers, save applications etc */

    public Component createActionsPanel(){

	// Left-hand action panels
	int ypos, xpos;
	if(OBSERVING_MODE){

	    // Now the actions panel 
	    _actionPanel = new JTabbedPane();

	    // Setup panel, two alternatives depending upon whether
	    // we are in expert mode or not. The easy one first
	    _noddySetupPanel = new JPanel(gbLayout);
	    _noddySetupPanel.setLayout(gbLayout);
	    
	    // Top-left
	    ypos = 0;
	    xpos = 0;
	    
	    // Sets up everything, checking each command along the way
	    setupAll.addActionListener(
				       new ActionListener(){
					   public void actionPerformed(ActionEvent e){
					       boolean ok = true;
					       
						   // RDGH: changed from RCO+RST to SRS for NTT 2010
					       if(ok && (ok = _execCommand("SRS", true)))
							   onResetAll();
					       
					       if(ok && (ok = _setupServers(false)))
							   onServersSet();
					       
					       if(ok && (ok = (_execRemoteApp(POWER_ON, false) && _execCommand("GO", false))))
							   onPowerOn();
					       
					       if(!ok)
						   logPanel.add("Combination ULTRACAM setup failed; if any of the commands were reported to be successful, you may want to " + 
								" switch to expert mode to try to continue with individual commands", LogPanel.WARNING, false);
					       else
						   logPanel.add("ULTRACAM successfully setup.", LogPanel.OK, true);
					   }
				       });
	    addActionComponent( _noddySetupPanel, setupAll, xpos, ypos++);
	    
	    // Top-right for the power off command.
	    ypos = 0;
	    xpos = 1;
	    
	    // Power off SDSU. Command needed in both panels
	    noddyPowerOff.addActionListener(
					    new ActionListener(){
						public void actionPerformed(ActionEvent e) {
						    if(_execRemoteApp(POWER_OFF, true) && _execCommand("GO", false))
							onPowerOff();
						}
					    });
	    addActionComponent( _noddySetupPanel, noddyPowerOff, xpos, ypos);
	    
	    // Now the expert one
	    _expertSetupPanel = new JPanel(gbLayout);
	    _expertSetupPanel.setLayout(gbLayout);
	    
	    // Top-left
	    ypos = 0;
	    xpos = 0;
	    
	    // Reset controller
	    resetSDSUhard.addActionListener(
					new ActionListener(){
					    public void actionPerformed(ActionEvent e){
						if(_execCommand("RCO", true))
						    onResetSDSU("hardware");
					    }
					});
	    addActionComponent( _expertSetupPanel, resetSDSUhard, xpos, ypos++);

		resetSDSUsoft.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if (_execCommand("RS", true)) {
							onResetSDSU("software");
						}
					}
				});
		addActionComponent( _expertSetupPanel, resetSDSUsoft, xpos, ypos++);
	    
	    // Reset PCI board
	    resetPCI.addActionListener(
				new ActionListener(){
				   public void actionPerformed(ActionEvent e){
				       if(_execCommand("RST", true))
						   onResetPCI();
					   }
				   });
	    addActionComponent( _expertSetupPanel, resetPCI, xpos, ypos++);

		resetAll.addActionListener(
				new ActionListener(){
					public void actionPerformed(ActionEvent e){
						if(_execCommand("SRS",true))
							onResetAll();
					}
				});
		addActionComponent(_expertSetupPanel, resetAll, xpos, ypos++);

	    // Setup servers
	    setupServer.addActionListener(
					  new ActionListener(){
					      public void actionPerformed(ActionEvent e) {
						  if(_setupServers(true))
						      onServersSet();
					      }
					  });
	    addActionComponent( _expertSetupPanel, setupServer, xpos, ypos++);
	    
	    // Power on SDSU
	    powerOn.addActionListener(
				      new ActionListener(){
					  public void actionPerformed(ActionEvent e) {
					      if(_execRemoteApp(POWER_ON, true) && _execCommand("GO", false))
						  onPowerOn();
					  }
				      });
	    addActionComponent( _expertSetupPanel, powerOn, xpos, ypos++);


		addActionComponent( _expertSetupPanel, _commandText, xpos++, ypos);
	    execExpertCmd.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e){
						_execCommand(_commandText.getText(),true);
					}
				});
		addActionComponent( _expertSetupPanel, execExpertCmd, xpos, ypos++);
	    

	    // Top-right for the power off command.
	    ypos = 0;
	    xpos = 1;
	    
	    // Power off SDSU. Command needed in both panels
	    expertPowerOff.addActionListener(
					     new ActionListener(){
						 public void actionPerformed(ActionEvent e) {
						     if(_execRemoteApp(POWER_OFF, true) && _execCommand("GO", false))
							 onPowerOff();
						 }
					     });

	    addActionComponent( _expertSetupPanel, expertPowerOff, xpos, ypos++);
	    
	    // Add in to the card layout
	    if(EXPERT_MODE)
		_actionPanel.insertTab("Setup", null, _expertSetupPanel, null, 0);
	    else
		_actionPanel.insertTab("Setup", null, _noddySetupPanel, null, 0);
	    
	}
	
	// Observing panel
	JPanel _obsPanel = new JPanel(gbLayout);
	
	// Top-left
	ypos = 0;
	xpos = 0;
	
	// Load application to local disk file
	loadApp.addActionListener(
				  new ActionListener(){
				      public void actionPerformed(ActionEvent e) {
					  if(_chooseLoadApp())
					      _loadApp(false);
				      }
				  });
	addActionComponent( _obsPanel, loadApp, xpos, ypos++);
	
	// Save application to local disk file
	JButton saveApp = new JButton("Save application");
	saveApp.addActionListener(
				  new ActionListener(){
				      public void actionPerformed(ActionEvent e){
					  if(_chooseSaveApp())
					      _saveApp();
				      }
				  });
	addActionComponent( _obsPanel, saveApp, xpos, ypos++);
	
	// By-pass the enforced save
	enableChanges.setEnabled(EXPERT_MODE || _unsavedSettings);
	enableChanges.addActionListener(
					new ActionListener(){
					    public void actionPerformed(ActionEvent e) {
						_unsavedSettings = false;
						_enableAll();
					    }
					});
	addActionComponent( _obsPanel, enableChanges, xpos, ypos++);

	// Ensure that binned windows match a standard phasing (designed so that there are no gaps
	// in the middle of the chip
	syncWindows.setEnabled(false);
	syncWindows.addActionListener(
				      new ActionListener(){
					  public void actionPerformed(ActionEvent e) {
					      if(_syncWindows()){
						  syncWindows.setEnabled(false);						  
						  syncWindows.setBackground(DEFAULT_COLOUR);
					      }
					  }
				      });
	addActionComponent( _obsPanel, syncWindows, xpos, ypos++);

	// Timing data
	JButton readSpeed = new JButton("Timing details");
	readSpeed.addActionListener(
				    new ActionListener(){
					public void actionPerformed(ActionEvent e) {
					    speed(DETAILED_TIMING);
					}
				    });
	addActionComponent( _obsPanel, readSpeed, xpos, ypos++);


	// Slide Control Button
	JButton slideCon = new JButton("Focal Plane Mask");
	slideCon.addActionListener(
				   new ActionListener(){
				       public void actionPerformed(ActionEvent e) {
					   SwingUtilities.invokeLater(new Runnable() {
						   public void run() {
						       SlideController sc = new SlideController();
						       //JFrame newframe = new JFrame("Slide Controller");
						       //newframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

						       //newframe.add(new SlideController());
						       //newframe.pack();
						       //newframe.setVisible(true);
						   }
					       });
				       }
				   });
	addActionComponent( _obsPanel, slideCon, xpos, ypos++);
	
	if(OBSERVING_MODE){
	    
	    // Top-right
	    ypos = 0;
	    xpos = 1;
	    
	    // Post application to the servers
	    postApp.addActionListener(
		new ActionListener(){
		    public void actionPerformed(ActionEvent e){
			
			if(CONFIRM_ON_CHANGE && (_objectText.getText().equals(""))){
			    int result = JOptionPane.showConfirmDialog(Udriver.this, 
								       "Target and/or filters field is blank. Are you happy to proceed?", "Confirm blank field(s)", JOptionPane.YES_NO_OPTION);
			    if(result == JOptionPane.NO_OPTION){
				logPanel.add("Application was not posted to the servers", LogPanel.WARNING, false);
				return;
			    }
			    
			}else if(CONFIRM_ON_CHANGE && _format.hasChanged() && _runType.equals("data")){
			    int result = JOptionPane.showConfirmDialog(Udriver.this, 
								       "Format has changed with no target name change; is the current target (" + 
								       _objectText.getText() + ") correct?", "Confirm target name", JOptionPane.YES_NO_OPTION);
			    if(result == JOptionPane.NO_OPTION){
				logPanel.add("Application was not posted to the servers", LogPanel.WARNING, false);
				return;
			    }
			    _format.update();
			}
			
			if(_postApp()){
			    onPostApp();
			}else{
			    logPanel.add("Failed to post application to servers", LogPanel.ERROR, false);
			}
		    }
		});
	    
	    addActionComponent( _obsPanel, postApp, xpos, ypos++);
	    
	    // Start a run
	    startRun.addActionListener(
		new ActionListener(){
		    public void actionPerformed(ActionEvent e){
			if(isRunActive(true)){
			    int result = JOptionPane.showConfirmDialog(Udriver.this, 
								       "A run may already be active. Do you really want to try to start another?", 
								       "Confirm start of run", JOptionPane.YES_NO_OPTION);
			    if(result == JOptionPane.NO_OPTION){
				logPanel.add("Failed to post application to servers", LogPanel.ERROR, false);
				return;
			    }
			    
			}else{
			    // Update run number, will be incremented by 'onStartRun' if start of run is successful.
			    getRunNumber();
			}
			
			if(_execCommand("GO", true))
			    onStartRun();			       
		    }
		});
	    addActionComponent( _obsPanel, startRun, xpos, ypos++);
	    
	    // Stop a run. 
	    stopRun.addActionListener(
		new ActionListener(){
		    public void actionPerformed(ActionEvent e){
				// 23/03/2010 RDGH -- Changed "ST" to "EX,0"
				if(_execCommand("EX,0", true)){
					onStopRun();
				}else{
					if(_runActive != null) _runActive.stop();
					_exposureMeter.stop();
				}
			}
		});
	    addActionComponent( _obsPanel, stopRun, xpos, ypos++);
	    
	    // Add in to the card layout
	    _actionPanel.insertTab("Observing", null, _obsPanel, null, 1);
	    _actionPanel.setBorder(new EmptyBorder(15,15,15,15));
	    
	    return _actionPanel;
	    
	}else{
	    
	    _obsPanel.setBorder(new EmptyBorder(15,15,15,15));
	    return _obsPanel;

	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Creates the panel displaying the timing & signal-to-noise information */
    public Component createTimingPanel(){
	
	// Timing info panel
	JPanel _timingPanel = new JPanel(gbLayout);
	
	int ypos = 0;
	
	addComponent( _timingPanel, new JLabel("Frame rate (Hz)"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_frameRate.setEditable(false);
	addComponent( _timingPanel, _frameRate, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JLabel cycle = new JLabel("Cycle time (s)");
	cycle.setToolTipText("Time from start of one exposure to the start of the next");
	addComponent( _timingPanel, cycle, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_cycleTime.setEditable(false);
	addComponent( _timingPanel, _cycleTime, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JLabel duty = new JLabel("Duty cycle (%)");
	duty.setToolTipText("Percentage of time spent gathering photons");
	addComponent( _timingPanel, duty, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_dutyCycle.setEditable(false);
	addComponent( _timingPanel, _dutyCycle, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	addComponent( _timingPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JLabel totalLabel = new JLabel("Total counts/exposure");
	totalLabel.setToolTipText("Total counts/exposure in object, for an infinite radius photometric aperture");
	addComponent( _timingPanel, totalLabel, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_totalCounts.setEditable(false);
	addComponent( _timingPanel, _totalCounts, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JLabel peakLabel = new JLabel("Peak counts/exposure  ");
	peakLabel.setToolTipText("In a binned pixel");
	addComponent( _timingPanel,  peakLabel, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_peakCounts.setEditable(false);
	addComponent( _timingPanel, _peakCounts, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JLabel stonLabelOne = new JLabel("S-to-N");
	stonLabelOne.setToolTipText("Signal-to-noise in one exposure, 1.5*seeing aperture");
	addComponent( _timingPanel,  stonLabelOne, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_signalToNoiseOne.setEditable(false);
	addComponent( _timingPanel, _signalToNoiseOne, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JLabel stonLabel = new JLabel("S-to-N, 3 hr");
	stonLabel.setToolTipText("Total signal-to-noise in a 3 hour run, 1.5*seeing aperture");
	addComponent( _timingPanel,  stonLabel, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_signalToNoise.setEditable(false);
	addComponent( _timingPanel, _signalToNoise, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Extras if we are observing
	
	if(OBSERVING_MODE){
	    
	    addComponent( _timingPanel, Box.createVerticalStrut(15), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    JLabel expStart = new JLabel("Exposure time");
	    expStart.setToolTipText("Time since 'Start exposure' was pressed");
	    addComponent( _timingPanel, expStart, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    _exposureTime.setEditable(false);
	    addComponent( _timingPanel, _exposureTime, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    JLabel diskSpace = new JLabel("Disk space used (MB)");
	    diskSpace.setToolTipText("Disk space used, an estimate only");
	    addComponent( _timingPanel, diskSpace, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    _spaceUsed.setEditable(false);
	    addComponent( _timingPanel, _spaceUsed, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    JLabel runNumber = new JLabel("Run number");
	    runNumber.setToolTipText("Last run number or current run if one is in progress");
	    addComponent( _timingPanel, runNumber, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    _runNumber.setEditable(false);
	    addComponent( _timingPanel, _runNumber, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    // Define timer to provide an exposure meter
	    
	    // Add to the seconds and the amount of space fields
	    ActionListener addSecond = new ActionListener() {
		    public void actionPerformed(ActionEvent event) {
			int nexpose = Integer.parseInt(Udriver.this._exposureTime.getText());
			nexpose++;
			Udriver.this._exposureTime.setText(String.valueOf(nexpose));
			int nmeg = (int)(((nexpose/_timePerImage)*_nbytesPerImage)/1024/1024 + 0.5);
			Udriver.this._spaceUsed.setText(String.valueOf(nmeg));
			if(nmeg > DISK_SPACE_DANGER){
			    Udriver.this._spaceUsed.setBackground(ERROR_COLOUR);
			}else if(nmeg > DISK_SPACE_WARN){
			    Udriver.this._spaceUsed.setBackground(WARNING_COLOUR);
			}else{
			    Udriver.this._spaceUsed.setBackground(DEFAULT_COLOUR);
			}
		    }
		};	
	    
	    // Timer is activated once per second
	    _exposureMeter = new Timer(1000, addSecond);
	    
	    // Define the action for a timer to check for an active run. This is needed in the case
	    // of a finite number of exposures since otherwise there is no way to tell
	    // that the run has stopped
	    _checkRun = new ActionListener() {
		    public void actionPerformed(ActionEvent event) {
			if(isRunActive(true)){
			    startRun_enabled        = false;
			    stopRun_enabled         = true;
			    postApp_enabled         = false;
			    resetSDSU_enabled       = false;
			    resetPCI_enabled        = false;
			    setupServer_enabled     = false;
			    powerOn_enabled         = false;
			    powerOff_enabled        = false;
			}else{
			    onStopRun();
			}
		    }
		};
	}    
	_timingPanel.setBorder(new EmptyBorder(15,15,15,15));	
	return _timingPanel;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Creates the panel which defines the window parameters */
    public Component createWindowPanel(){

	int ypos = 0;

	JPanel _windowPanel     = new JPanel( gbLayout );
	_windowPanel.setBorder(new EmptyBorder(15,15,15,15));
	    
	// Application (drift etc)
	if(TEMPLATE_LABEL.length > 1){

	    JLabel templateLabel = new JLabel("Template type");
	    addComponent( _windowPanel, templateLabel, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	}

	templateChoice = new JComboBox(TEMPLATE_LABEL);
	templateChoice.setSelectedItem(applicationTemplate);
	templateChoice.setMaximumRowCount(TEMPLATE_LABEL.length);

	if(TEMPLATE_LABEL.length > 1){

	    // The main thing to do here is disable irrelevant parts according to 
	    // the application
	    templateChoice.addActionListener(
		new ActionListener(){
		    public void actionPerformed(ActionEvent e){
			
			applicationTemplate = (String) templateChoice.getSelectedItem();
			setNumEnable();
			_windowPairs.setNpair(numEnable);

			if(numEnable == 0)
			    _setWinLabels(false);
			else
			    _setWinLabels(true);
			
			// See if we need to check for the mask 
			if(CHECK_FOR_MASK &&
			   ((applicationTemplate.equals("Drift mode")  && !oldApplicationTemplate.equals("Drift mode")) ||
			    (!applicationTemplate.equals("Drift mode") && oldApplicationTemplate.equals("Drift mode")))){
			    JOptionPane.showMessageDialog(null, "Please confirm that the focal plane mask is correctly positioned", 
							  "Confirm focal plane mask position", JOptionPane.WARNING_MESSAGE);
			}
			oldApplicationTemplate = applicationTemplate;
		    }
		});
	    
	    // Add to the panel
	    addComponent( _windowPanel, templateChoice, 1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	}

	// Readout speed selection
	addComponent( _windowPanel,  new JLabel("Readout speed"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	speedChoice = new JComboBox(SPEED_LABELS);
	speedChoice.setSelectedItem(readSpeed);
	addComponent( _windowPanel, speedChoice, 1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// Exposure time
	addComponent( _windowPanel, new JLabel("Exposure delay (millisecs)   "), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Might need to adjust fine increment after a change of exposure time
	exposeText.addActionListener(new ActionListener(){
		public void actionPerformed(ActionEvent e){
		    if(!EXPERT_MODE){
			try {
			    int n = exposeText.getValue();

			    if(n == 0)
				tinyExposeText.setValue(5);
			    else
				tinyExposeText.setValue(0);
			    expose = 5;

			} 
			catch (Exception er) {
			    tinyExposeText.setValue(0);
			}
		    }
		}
	    });

	JPanel exp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
	exp.add(exposeText);

	exp.add(new JLabel(" . "));
	tinyExposeText.setEnabled(EXPERT_MODE);
	exp.add(tinyExposeText);

	addComponent( _windowPanel, exp, 1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Number of exposures
	addComponent( _windowPanel, new JLabel("Number of exposures"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, numExposeText, 1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// The binning factors
	addComponent( _windowPanel, new JLabel("Binning factors (X, Y)"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, xbinText,  1, ypos,    1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, ybinText,  2, ypos++,  2, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// The u'-band coadds
	addComponent( _windowPanel, new JLabel("u-band coadds"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, nblueText, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// Add some space before we get onto the window definitions
	addComponent( _windowPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// Window definition lines
	setNumEnable();
	
	// First the labels for each column
	ystartLabel.setToolTipText("Y value of lowest row of " + WINDOW_NAME);
	xleftLabel.setToolTipText("X value of first column of left-hand window");
	xrightLabel.setToolTipText("X value of first column of right-hand window");
	nxLabel.setToolTipText("Number of unbinned pixels in X of each window of pair");
	nyLabel.setToolTipText("Number of unbinned pixels in Y of each window of pair");

	int xpos = 0;
	addComponent( _windowPanel, windowsLabel, xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, ystartLabel,  xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, xleftLabel,   xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, xrightLabel,  xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, nxLabel,      xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, nyLabel,      xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	ypos++;
	
	// Then the row labels and fields for integer input
	_windowPairs = new WindowPairs(gbLayout, _windowPanel, ypos, xbin, ybin, DEFAULT_COLOUR, ERROR_COLOUR, specialNy);
	_windowPairs.setNpair(numEnable);
	ypos += 3;
	
	// Add some space between window definitions and the user-defined stuff
	addComponent( _windowPanel, Box.createVerticalStrut(20), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	if(OBSERVING_MODE){
   
	    addComponent( _windowPanel, new JLabel("Target name"),     0, ypos,  1, 1,
                      GridBagConstraints.NONE, GridBagConstraints.WEST);
	    addComponent( _windowPanel, _objectText,     1, ypos,  5, 1,
                      GridBagConstraints.NONE, GridBagConstraints.WEST);

        /* comment out simbad verification as its flaky */
        /*
		final JButton lookupButton = new JButton("Verify");
		addComponent( _windowPanel, lookupButton, 5, ypos++, 5, 1,
                      GridBagConstraints.NONE, GridBagConstraints.WEST);
		lookupButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    if (_verifyTarget(_objectText.getText())) {
                        lookupButton.setBackground(GO_COLOUR); 
                    } else {
                        lookupButton.setBackground(ERROR_COLOUR); 
                    } 
                }
            });
		_objectText.addKeyListener(new KeyListener(){
			public void keyPressed(KeyEvent e){ lookupButton.setBackground(DEFAULT_COLOUR); }
			public void keyReleased(KeyEvent e) {}
			public void keyTyped(KeyEvent e) {}
		});
        */
        ypos++;

		addComponent(
                     _windowPanel, new JLabel("Run type"), 0, ypos, 1, 1,
                     GridBagConstraints.NONE, GridBagConstraints.WEST
                     );

		_dataButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_runType = "data"; _acquisitionState = false; _checkEnabledFields();}});
		addComponent( _windowPanel, _dataButton,     1, ypos,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_dataButton.setSelected(true);
		_acqButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_runType = "data"; _acquisitionState = true; _checkEnabledFields();}});
		addComponent( _windowPanel, _acqButton,     3, ypos,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_biasButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_runType = "bias"; _acquisitionState = false; _checkEnabledFields();}});
		addComponent( _windowPanel, _biasButton,     5, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_flatButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_runType = "flat"; _acquisitionState = false; _checkEnabledFields();}});
		addComponent( _windowPanel, _flatButton,     1, ypos,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_darkButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_runType = "dark"; _acquisitionState = false; _checkEnabledFields();}});
		addComponent( _windowPanel, _darkButton,     3, ypos,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_techButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_runType = "technical"; _acquisitionState = false; _checkEnabledFields();}});
		addComponent( _windowPanel, _techButton,     5, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		ButtonGroup runTypeGroup = new ButtonGroup();
		runTypeGroup.add(_dataButton);
		runTypeGroup.add(_biasButton);
		runTypeGroup.add(_flatButton);
		runTypeGroup.add(_darkButton);
		runTypeGroup.add(_techButton);
		runTypeGroup.add(_acqButton);
	    
	    addComponent( _windowPanel, new JLabel("Filters"),     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_filter1 = new JComboBox(BLUE_FILTER_NAMES);
		_filter1.setSelectedItem(defaultFilter1);
		addComponent( _windowPanel, _filter1, 1, ypos, 5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_filter2 = new JComboBox(GREEN_FILTER_NAMES);
		_filter2.setSelectedItem(defaultFilter2);
		addComponent( _windowPanel, _filter2, 3, ypos, 5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		_filter3 = new JComboBox(RED_FILTER_NAMES);
		_filter3.setSelectedItem(defaultFilter3);
		addComponent( _windowPanel, _filter3, 5, ypos++, 5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    addComponent( _windowPanel, new JLabel("Programme ID"),     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    addComponent( _windowPanel, _progidText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    addComponent( _windowPanel, new JLabel("Principal Investigator"),     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    addComponent( _windowPanel, _piText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    addComponent( _windowPanel, new JLabel("Observer(s)"),     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    addComponent( _windowPanel, _observerText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	}
	
	// Add a border
	_windowPanel.setBorder(new EmptyBorder(15,15,15,15));	
	return _windowPanel;
    }

    /** Creates the panel defining the target information */
    public Component createTargetPanel(){
    
	int ypos = 0;
	
	// Target info panel
	JPanel _targetPanel = new JPanel(gbLayout);
	
	JLabel bandLabel = new JLabel("Bandpass");
	bandLabel.setToolTipText("Bandpass for estimating counts and signal-to-noise");
	addComponent( _targetPanel, bandLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Create radio buttons for the filters
	JRadioButton uButton = new JRadioButton("u'     ");
	uButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_filterIndex = 0;}});
	addComponent( _targetPanel, uButton,     1, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JRadioButton gButton = new JRadioButton("g'     ");
	gButton.setSelected(true);
	gButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_filterIndex = 1;}});
	addComponent( _targetPanel, gButton,     2, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JRadioButton rButton = new JRadioButton("r'     ");
	rButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_filterIndex = 2;}});
	addComponent( _targetPanel, rButton,     3, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JRadioButton iButton = new JRadioButton("i'     ");
	iButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_filterIndex = 3;}});
	addComponent( _targetPanel, iButton,     4, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JRadioButton zButton = new JRadioButton("z'");
	zButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_filterIndex = 4;}});
	addComponent( _targetPanel, zButton,     5, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Group the radio buttons.
	ButtonGroup fGroup = new ButtonGroup();
	fGroup.add(uButton);
	fGroup.add(gButton);
	fGroup.add(rButton);
	fGroup.add(iButton);
	fGroup.add(zButton);

	JLabel magLabel = new JLabel("Magnitude");
	magLabel.setToolTipText("Magnitude at airmass=0 for estimating counts and signal-to-noise");
	addComponent( _targetPanel, magLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _targetPanel, _magnitudeText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	JLabel seeingLabel = new JLabel("Seeing (FWHM, arcsec)     ");
	seeingLabel.setToolTipText("FWHM seeing. Aperture assumed to be 1.5 times this.");
	addComponent( _targetPanel, seeingLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _targetPanel, _seeingText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JLabel skyBackLabel = new JLabel("Sky brightness");
	addComponent( _targetPanel, skyBackLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// Create radio buttons for the sky brightness
	JRadioButton darkButton = new JRadioButton("dark");
	darkButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_skyBrightIndex = 0;}});
	addComponent( _targetPanel, darkButton,     1, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JRadioButton greyButton = new JRadioButton("grey");
	greyButton.setSelected(true);
	greyButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_skyBrightIndex = 1;}});
	addComponent( _targetPanel, greyButton,     2, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	JRadioButton brightButton = new JRadioButton("bright");
	brightButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){_skyBrightIndex = 2;}});
	addComponent( _targetPanel, brightButton,     3, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Group the radio buttons.
	ButtonGroup sGroup = new ButtonGroup();
	sGroup.add(darkButton);
	sGroup.add(greyButton);
	sGroup.add(brightButton);

	JLabel airmassLabel = new JLabel("Airmass");
	addComponent( _targetPanel, airmassLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _targetPanel, _airmassText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	_targetPanel.setBorder(new EmptyBorder(15,15,15,15));	
	return _targetPanel;
    }
}

    
