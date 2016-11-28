import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.magiclen.dialog.Dialog.DialogButtonEvent;



import org.magiclen.dialog.Dialogs;



import dao.Dao;
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import io.Writer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;

public class Controller implements Initializable {
	
	boolean debug=false; //開發階段
	
	int interval=1000;//預設間隔時間	

	Dao dao=new Dao();

	public AreaChart sc;	
	public AreaChart sc_h;
	public AreaChart sc_d;
	public AreaChart sc_w;
	public AreaChart sc_m;
	
	@FXML
	private TabPane tab;
	@FXML
	public Label val;	
	@FXML
	public Label valx;	
	@FXML
	public Label yearValue;		
	@FXML
	public Label timeValue;	
	public ExecutorService executor;
	private AddToQueue addToQueue;

	static int num;	
	static int n; //取值
	int n1;
	
	private void saveData() throws SQLException, IOException {
		dao.exSql("INSERT INTO adlog(val,original)VALUES("+num+","+n+");");
		//if(n>mv)mv=num;
		//if(n<nv)nv=num;
	}
	
	final SimpleDateFormat sf=new SimpleDateFormat("yyyy-MM-dd");
	final SimpleDateFormat sf1=new SimpleDateFormat("HH:mm.ss");
	String day, time;
	Date now;
	
	//更新時間
	private void setTime(){
		now=new Date();
		day=sf.format(now);
		time=sf1.format(now);	
	}	
	
	int cs, cm, ch, cd;
	int s, smin, smax, m, min, max, h, hmin, hmax, d, dmin, dmax, w, wmin, wmax;	
	
	//計數器
	private void count(){		
		cs++;		
		dataQMini.add(nv);
		dataQMax.add(mv);
		if(s==0){//秒計為0時-取每分鐘值
			smin=num;
			smax=num;
		}else{
			if(num>smax)smax=num;
			if(num<smin)smin=num;
		}
		
		if(m==0){//分計為0時-取每小時值
			min=num;
			max=num;
		}else{
			if(num>max)max=num;
			if(num<min)min=num;
		}
		
		if(h==0){//分計為0時-取每小時值
			hmin=num;
			hmax=num;
		}else{
			if(num>hmax)hmax=num;
			if(num<hmin)hmin=num;
		}
		if(d==0){//分計為0時-取每小時值
			dmin=num;
			dmax=num;
		}else{
			if(num>dmax)dmax=num;
			if(num<dmin)dmin=num;
		}
		if(h==0){//分計為0時-取每小時值
			wmin=num;
			wmax=num;
		}else{
			if(num>wmax)wmax=num;
			if(num<wmin)wmin=num;
		}
		//smin=(num<smin)?num:smin;
		//smax=(num>smax)?num:smax;		
		s+=num;
		if(cs==60){
			m+=(s/60);//每分鐘平均值
			dataH.add(s/60);			
			dataHMini.add(smin);
			dataHMax.add(smax);
			cs=0;			
			s=0;
			cm++;
		}
		
		if(cm==60){
			h+=(m/60);//每小時平均值
			dataD.add(m/60);
			dataDMini.add(min);
			dataDMax.add(max);			
			cm=0;
			m=0;
			ch++;
		}
		
		if(ch==24){
			d+=(h/24);//每天平均
			dataW.add(h/24);
			dataWMini.add(hmin);
			dataWMax.add(hmax);			
			ch=0;
			h=0;
			cd++;
		}
		
		if(cd==7){
			dataM.add(d/7);//每週平均
			dataMMini.add(dmin);
			dataMMax.add(dmax);			
			cd=0;
			d=0;
		}
	}	
	
	//加權值	
	final static double POW=0.00025;	
	private static int pow(int n){		
		//System.out.println("n="+n);
		if(n>4000 && n<=8000){			
			return (int)Math.pow(10, 1+((n-4000)*POW));
		}		
		if(n>8000 && n<=12000){	
			return (int)Math.pow(10, 2+((n-8000)*POW));
		}
		if(n>12000 && n<=16000){
			return (int)Math.pow(10, 3+((n-12000)*POW));
		}
		if(n>16000 && n<=20000){
			return (int)Math.pow(10, 4+((n-16000)*POW));
		}
		return 99999;
	}
	
	static int mv, maxx=0;//取樣取大值
	static int nv, minn=99999;//取樣最小值
    static int vc;//取樣次數
    short r;
	
    @FXML
    public Label avgValue;
    @FXML
    public Label minValue;
    @FXML
    public Label maxValue;
    @FXML
    public Label sps;
    @FXML
    public Label LabSps;
    @FXML
    public Label LabMax;
    @FXML
    public Label LabMin;
    @FXML
    public Label LabAvg;
    @FXML
    public Label LabVal;    
	
	//資料排入駐列
	private class AddToQueue implements Runnable {
		public void run() {
			//開發期間以亂數
			if(debug){	
				n=(int)(Math.random()*(400-395+1))+400;				
				nv=n-(int)(Math.random()*(50-1+1))+1;
        		mv=n+(int)(Math.random()*(50-1+1))+1;
        		//num=pow(mv);
        		num=mv;
        		vc=(int)(Math.random()*(56-30+1))+30;        		      		
			}else{			
				num=vc;				
			}			
			//每次間隔時間執行工作
			count();
			setTime();//更新時間
			try {				
					if(!debug){
					saveData();//儲存資料		    	
				}
				Thread.sleep(interval);				
			} catch (Exception e) {
				e.printStackTrace();
			}
			executor.execute(this);
			// 同步更新需起另1個執行緒
			Platform.runLater(new Runnable() {
				@Override
				public void run() {					
					valx.setText(n+"");	
					val.setText(num+"");					
					yearValue.setText(day);
					timeValue.setText(time);					
					maxValue.setText(String.valueOf(maxx));
					minValue.setText(String.valueOf(minn));
					
					//更新圖表
					addDataToSeries();
					if(cs==0){addDataToHourSeries();}
					if(cm==0){addDataToDaySeries();}
					if(ch==0){addDataToWeekSeries();}
					if(cd==0){addDataToMonthSeries();}
				}
			});
		}
	}
	
	//每分鐘圖表
	@FXML
	private NumberAxis xAxis;
	@FXML
	private NumberAxis yAxis;
	
	private ConcurrentLinkedQueue<Number> dataQMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataQMax = new ConcurrentLinkedQueue<Number>();
	private Series series_min;
	private Series series_max;
	private int seriesData = 0;
	
	private void addDataToSeries() {		
		for (int i = 0; i < 60; i++) {
			if (dataQMax.isEmpty()){
				break;
			}			
			series_min.getData().add(new AreaChart.Data(seriesData++, dataQMini.remove()));			
			series_max.getData().add(new AreaChart.Data(seriesData-1, dataQMax.remove()));
		}
		if (series_min.getData().size() > 60) {
			series_min.getData().remove(0, (series_min.getData().size() - 61));
			series_max.getData().remove(0, (series_max.getData().size() - 61));
		}
		xAxis.setLowerBound(seriesData - 60);
		xAxis.setUpperBound(seriesData - 1);		
	}
	
	//每小時圖表
	@FXML
	private NumberAxis xAxis_h;
	@FXML
	private NumberAxis yAxis_h;
	
	private Series series_h;
	private Series series_h_min;
	private Series series_h_max;
	
	private int seriesData_h = 0;
	
	private ConcurrentLinkedQueue<Number> dataH = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataHMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataHMax = new ConcurrentLinkedQueue<Number>();
	
	private void addDataToHourSeries() {		
		
		for (int i = 0; i < 61; i++) {
			if(dataH.isEmpty()){break;}
			series_h.getData().add(new AreaChart.Data(seriesData_h++, dataH.remove()));				
			series_h_min.getData().add(new AreaChart.Data(seriesData_h-1, dataHMini.remove()));			
			series_h_max.getData().add(new AreaChart.Data(seriesData_h-1, dataHMax.remove()));			
		}
		if (series_h.getData().size() > 61){
			series_h.getData().remove(0, (series_h.getData().size() - 61));
			series_h_min.getData().remove(0, (series_h_min.getData().size() - 61));
			series_h_max.getData().remove(0, (series_h_max.getData().size() - 61));
		}
		xAxis_h.setLowerBound(seriesData_h - 60);
		xAxis_h.setUpperBound(seriesData_h - 1);
	}
	
	//每日圖表
	@FXML
	private NumberAxis xAxis_d;
	@FXML
	private NumberAxis yAxis_d;
	
	private Series series_d;
	private Series series_d_min;
	private Series series_d_max;
	
	private int seriesData_d = 0;
	
	private ConcurrentLinkedQueue<Number> dataD = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataDMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataDMax = new ConcurrentLinkedQueue<Number>();
	private void addDataToDaySeries() {
		for (int i = 0; i < 25; i++) {
			if (dataD.isEmpty()){
				break;
			}	
			series_d.getData().add(new AreaChart.Data(seriesData_d++, dataD.remove()));			
			series_d_min.getData().add(new AreaChart.Data(seriesData_d-1, dataDMini.remove()));
			series_d_max.getData().add(new AreaChart.Data(seriesData_d-1, dataDMax.remove()));
		}
		if (series_d.getData().size() > 25) {
			series_d.getData().remove(0, (series_d.getData().size() - 25));
			series_d_min.getData().remove(0, (series_d_min.getData().size() - 25));
			series_d_max.getData().remove(0, (series_d_max.getData().size() - 25));
		}
		xAxis_d.setLowerBound(seriesData_d - 24);
		xAxis_d.setUpperBound(seriesData_d - 1);
	}
	
	//每週圖表
	@FXML
	private NumberAxis xAxis_w;
	@FXML
	private NumberAxis yAxis_w;
	
	private Series series_w;
	private Series series_w_min;
	private Series series_w_max;
	
	private int seriesData_w = 0;
	
	private ConcurrentLinkedQueue<Number> dataW = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataWMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataWMax = new ConcurrentLinkedQueue<Number>();
	private void addDataToWeekSeries() {
		
		for (int i = 0; i < 8; i++) {
			if (dataW.isEmpty()){
				break;
			}	
			series_w.getData().add(new AreaChart.Data(seriesData_w++, dataW.remove()));			
			series_w_min.getData().add(new AreaChart.Data(seriesData_w-1, dataWMini.remove()));
			series_w_max.getData().add(new AreaChart.Data(seriesData_w-1, dataWMax.remove()));
		}
		
		if (series_w.getData().size() > 8) {
			series_w.getData().remove(0, (series_w.getData().size() - 8));
			series_w_min.getData().remove(0, (series_w_min.getData().size() - 8));
			series_w_max.getData().remove(0, (series_w_max.getData().size() - 8));
		}
		xAxis_w.setLowerBound(seriesData_w - 7);
		xAxis_w.setUpperBound(seriesData_w - 1);
	}
	
	//每月圖表
	@FXML
	private NumberAxis xAxis_m;
	@FXML
	private NumberAxis yAxis_m;
	
	private Series series_m;
	private Series series_m_min;
	private Series series_m_max;
	
	private int seriesData_m = 0;
	
	private ConcurrentLinkedQueue<Number> dataM = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataMMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataMMax = new ConcurrentLinkedQueue<Number>();
	private void addDataToMonthSeries() {
		for (int i = 0; i < 5; i++) {
			if (dataM.isEmpty()){
				break;
			}	
			series_m.getData().add(new AreaChart.Data(seriesData_m++, dataM.remove()));			
			series_m_min.getData().add(new AreaChart.Data(seriesData_m-1, dataMMini.remove()));
			series_m_max.getData().add(new AreaChart.Data(seriesData_m-1, dataMMax.remove()));
		}
		if (series_m.getData().size() > 5) {
			series_m.getData().remove(0, (series_m.getData().size() - 5));
			series_m_min.getData().remove(0, (series_m_min.getData().size() - 5));
			series_m_max.getData().remove(0, (series_m_max.getData().size() - 5));
		}
		xAxis_m.setLowerBound(seriesData_m - 4);
		xAxis_m.setUpperBound(seriesData_m - 1);
	}    
	
	public void initialize(URL url, ResourceBundle rb) {		
		//TODO 平台變更時PORT名稱變更
		try {			
			if(!debug){
				this.connect("COM3");
			}else{
				this.connect("COM3");
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		//min
		series_min= new AreaChart.Series<Number, Number>();
		series_max= new AreaChart.Series<Number, Number>();
		xAxis.setForceZeroInRange(false);
		xAxis.setAutoRanging(false);
		xAxis.setTickLabelsVisible(false);
		
		sc.setAnimated(false);
		sc.setCreateSymbols(false);
		sc.getData().addAll(series_max, series_min);
		
		//hour
		series_h = new AreaChart.Series<Number, Number>();
		series_h_min= new AreaChart.Series<Number, Number>();
		series_h_max= new AreaChart.Series<Number, Number>();		
		xAxis_h.setForceZeroInRange(false);
		xAxis_h.setAutoRanging(false);
		xAxis_h.setTickLabelsVisible(false);
		sc_h.setAnimated(false);
		sc_h.setCreateSymbols(false);
		sc_h.getData().addAll(series_h_max, series_h, series_h_min);
		
		//day		
		series_d = new AreaChart.Series<Number, Number>();
		series_d_min= new AreaChart.Series<Number, Number>();
		series_d_max= new AreaChart.Series<Number, Number>();
		xAxis_d.setForceZeroInRange(false);
		xAxis_d.setAutoRanging(false);
		xAxis_d.setTickLabelsVisible(false);
		sc_d.setAnimated(false);
		sc_d.setCreateSymbols(false);
		sc_d.getData().addAll(series_d_max, series_d, series_d_min);
		
		//week
		series_w = new AreaChart.Series<Number, Number>();
		series_w_min= new AreaChart.Series<Number, Number>();
		series_w_max= new AreaChart.Series<Number, Number>();
		xAxis_w.setForceZeroInRange(false);
		xAxis_w.setAutoRanging(false);
		xAxis_w.setTickLabelsVisible(false);
		sc_w.setAnimated(false);
		sc_w.setCreateSymbols(false);
		sc_w.getData().addAll(series_w_max, series_w, series_w_min);
		
		//month
		series_m = new AreaChart.Series<Number, Number>();
		series_m_min= new AreaChart.Series<Number, Number>();
		series_m_max= new AreaChart.Series<Number, Number>();
		xAxis_m.setForceZeroInRange(false);
		xAxis_m.setAutoRanging(false);
		xAxis_m.setTickLabelsVisible(false);
		sc_m.setAnimated(false);
		sc_m.setCreateSymbols(false);
		sc_m.getData().addAll(series_m_max, series_m, series_m_min);
		
		executor = Executors.newCachedThreadPool();		
		addToQueue = new AddToQueue();
		executor.execute(addToQueue);	
	}
	
	//TODO 平台變更時路徑變更
	private String getPath(){		
		if(!debug){
			return "C:/dev/a2dv/";
		}else{			
			//File file=new File("/media/pi/");			
			//eturn "/media/pi/"+file.list()[0]+"/";
			//return "D:/";
			return "C:/dev/a2dv/";
		}		
	}
	
	private void write(List<Map>list, String fileName) throws SQLException{		
		Writer w=new Writer();
		
		File file=new File(getPath());
		if(!file.exists())file.mkdirs();		
		String path=getPath()+fileName;
		w.deleteFile(path);		
		w.createNewFile(path);			
		StringBuilder sb=new StringBuilder();
		if(list.size()>5000){
			for(int i=0; i<list.size(); i++){
				sb.append(list.get(i).get("time")+","+list.get(i).get("val")+","+list.get(i).get("original")+"\n");
				if(i%5000==0){
					w.writeText(sb.toString(),path,"utf8",true);
					sb=new StringBuilder();
				}
			}
			w.writeText(sb.toString(),path,"utf8",true);
		}else{
			for(int i=0; i<list.size(); i++){
				sb.append(list.get(i).get("time")+","+list.get(i).get("val")+","+list.get(i).get("original")+"\n");
			}
			w.writeText(sb.toString(),path,"utf8",true);
		}		
	}
	
	@FXML
	private void close(ActionEvent event) throws IOException {
		//TODO 最佳化MySQL
		/*if(!debug){
			
		}else{
			
		}*/
		//Process p2=Runtime.getRuntime().exec("/home/pi/AutoRepirMysql.sh");
		System.exit(0);
	}
	
	//關閉下載視窗
	private static final DialogButtonEvent Ev=new closeBtn();
	private static class closeBtn implements DialogButtonEvent{

		public void onClick() {
			
		}
	}
	
	private String getRange(int type){
		
		Calendar c=Calendar.getInstance();
		//day
		if(type==0){
			c.add(Calendar.DAY_OF_YEAR, -1);
		}
		//week
		if(type==1){
			c.add(Calendar.WEEK_OF_YEAR, -1);
		}
		//month
		if(type==2){
			c.add(Calendar.MONTH, -1);
		}
		//year
		if(type==3){
			c.add(Calendar.YEAR, -1);
		}
		return sf.format(c.getTime());
	}	
	
	@FXML
	private void save24(ActionEvent event) throws SQLException {		
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(0)+"'");		
		write(list, "log_day.csv");		
		
		Alert alert = new Alert(AlertType.INFORMATION);
		alert.setTitle("Information Dialog");
		alert.setHeaderText(getPath()+"log_day.csv");
		alert.setContentText("Saved successfully!");
		alert.show();
		/*Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();*/		
	}
	
	@FXML
	private void saveWeek(ActionEvent event) throws SQLException {		
		
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(1)+"'");		
		write(list, "log_week.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();	
	}
	
	@FXML
	private void saveMonth(ActionEvent event) throws SQLException {
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(2)+"'");		
		write(list, "log_month.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();
	}
	
	@FXML
	public DatePicker begin;
	
	@FXML
	public DatePicker end;
	
	@FXML
	private void saveCustom(ActionEvent event) throws SQLException {
		if(begin==null){
			begin.setValue(LocalDate.now());
		}
		if(end==null){
			end.setValue(LocalDate.now());
		}
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>='"+begin.getValue()+"'AND time<='"+end.getValue()+"'");		
		write(list, "log_custom.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();
	}
	
	@FXML
	private void saveYear(ActionEvent event) throws SQLException {
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(3)+"'");		
		write(list, "log_year.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();
	}
	
	@FXML
	public DatePicker sysdate;
	@FXML
	public Slider syshh;
	@FXML
	public Slider sysmm;
	@FXML
	public Label syshhVal;
	@FXML
	public Label sysmmVal;
	
	@FXML
	private void setime(ActionEvent event) {
		if(sysdate.getValue()!=null)
		try{
			
			Process p=Runtime.getRuntime().exec(new String[]{"date","--set",sysdate.getValue()+" "+syshhVal.getText()+":"+sysmmVal.getText()+":0.0"});
			
			Process p1=Runtime.getRuntime().exec(new String[]{"sudo", "hwclock","--systohc"});
			Thread.sleep(5000);
			Process p2=Runtime.getRuntime().exec(new String[]{"sudo", "reboot"});
			//p.destroy();
			//p1.destroy();
			//p=Runtime.getRuntime().exec("reboot");			
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	@FXML
	private void setSyshhVal(){
		syshhVal.setText((int)syshh.getValue()+"");
	}
	
	@FXML
	private void setSysmmVal(){
		sysmmVal.setText((int)sysmm.getValue()+"");
	}
	
	/**
	 * 特定格式轉換
	 * 第取3、4位置的bit資料，轉換為16進位數值
	 * 再將16進位轉為10進位
	 * @param b
	 * @return
	 */
	private static int converter(byte b[]){		
		String s=String.valueOf(toHex(b[3]))+String.valueOf(toHex(b[4]));		
		return Integer.parseInt(s, 16);
	}	

	/**
	 * 接收格式檢查
	 * 經常接收到格式錯亂的資料陣列
	 * 確實是裝置將值多送的問題, 但是格式正確的資料陣列也有可能重複
	 * 後來發現只要資料陣列第1個位置不是01就是有問題
	 * @return
	 * @throws InterruptedException 
	 */
	private static boolean checkout() throws InterruptedException{	
		if(buffer[0]!=01){
			t2.sleep(1000);
			System.out.println("error format! waited 1000! restart!");
			return false;
		}
		return true;
	}
	
	/**
	 * byte值轉16進制
	 * @param b
	 * @return
	 */
	private static String toHex(byte b){
		return (""+"0123456789ABCDEF".charAt(0xf&b>>4)+"0123456789ABCDEF".charAt(b&0xf));
	}
	
	static byte[] buffer = new byte[7];
	static int len = -1;
	
	static String path, file="data.json";
	
	static Thread t1, t2;
	
	/**
	 * 裝置連線
	 * @param portName
	 * @throws Exception
	 */
	private void connect(String portName) throws Exception {
		CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
		if (portIdentifier.isCurrentlyOwned()) {
			System.out.println("Error: Port is currently in use");
		} else {
			int timeout = 2000;
			CommPort commPort = portIdentifier.open(this.getClass().getName(), timeout);

			if (commPort instanceof SerialPort) {
				SerialPort serialPort = (SerialPort) commPort;
				serialPort.setSerialPortParams(19200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				InputStream in = serialPort.getInputStream();				
				OutputStream out = serialPort.getOutputStream();
				//(new Thread(new SerialWriter(out))).start();
				//(new Thread(new SerialReader(in))).start();
				t1=new Thread(new SerialWriter(out));
				t2=new Thread(new SerialReader(in));
				t1.start();
				t2.start();
				
			} else {
				System.out.println("連接埠使用中");
			}
		}
	}
	
	/**
	 * 接收資料
	 * @author shawn
	 */
	public static class SerialReader implements Runnable {

		InputStream in;

		public SerialReader(InputStream in) {
			this.in = in;
		}
		
		public void run() {			
			while(true){
				try {
					
					Thread.sleep(500);
					this.in.read(buffer);
					for(int i=0; i<6; i++){							
						System.out.print("["+buffer[i]+"] ");						
					}
					System.out.println();
					Thread.sleep(500);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 發送命令
	 * @author shawn
	 */
	public static class SerialWriter implements Runnable {
		OutputStream out;
		char com[]={0X01, 0X04, 0X10, 0X01, 0X00, 0X01, 0X64, 0XCA};
		public SerialWriter(OutputStream out) {
			this.out = out;
		}
		public void run() {			
			while (true) {
                try {
                    for(int i=0; i<com.length; i++){
                    	out.write(com[i]);
                    }
                    
                    if(checkout()){   
                    	n=converter(buffer);
                    	System.out.println(n);
                    	vc=pow(n);
                    	mv=vc;
                    	nv=vc;
                    	
                    	if(vc>maxx)maxx=vc;
                    	if(vc<minn)minn=vc;
                    }                    
                    Thread.sleep(1000);//發送命令間隔
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }			
		}
	}
	
}