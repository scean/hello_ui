package com.android.providers.downloads.ui.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 此处写类描述
 *
 * @author gaogb
 * @Package com.android.providers.downloads.notification
 * @ClassName: DateUtil
 * @mail gaoguibao@xunlei.com
 * @date 2014年6月17日 下午8:17:09
 */
public class DateUtil {
	public static final String FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss";
	public static final String FORMAT_DATE = "yyyy-MM-dd";
	public static final String FORMAT_TIME = "HH:mm:ss";
	public static final String FORMAT_YMD_TIME = "yyyy:MM:DD HHmmss";
	public static final String FORMAT_MD_HH = "MM-dd HH";
	public static final String FORMAT_YYMD_HH = "yyyy-MM-dd HH";

	/**
	 * get current datetime
	 *
	 * @return String
	 * @Title: getDatetime
	 * @date 2014年6月17日 下午8:17:23
	 */
	public static String getDatetime() {
		Calendar calendar = Calendar.getInstance();
		return getStringFromDate(calendar.getTime(), FORMAT_DATETIME);
	}

	/**
	 * get Current date
	 *
	 * @return String
	 * @Title: getDate
	 */
	public static String getDate() {
		return getDate(Calendar.getInstance());
	}

	/**
	 * get Current time
	 *
	 * @return String
	 * @Title: getDate
	 */
	public static String getTime() {
		Calendar calendar = Calendar.getInstance();
		return getStringFromDate(calendar.getTime(), FORMAT_TIME);
	}

	public static String getDate(Calendar calendar) {
		return getStringFromDate(calendar.getTime(), FORMAT_DATE);
	}

	/**
	 * 返回两个时间的相隔天数
	 *
	 * @param start
	 * @param end
	 * @return double
	 * @throws Exception
	 * @Title: getDiffDays
	 * @date 2014年6月17日 下午8:21:04
	 */
	public static double getDiffDays(String start, String end) throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_DATE);
		Date beginDate = sdf.parse(start);
		Date endDate = sdf.parse(end);
		double days = (endDate.getTime() - beginDate.getTime())
				/ (24 * 60 * 60 * 1000);

		return days;
	}

	/**
	 * 某日期加上几天得到另外一个日期
	 *
	 * @param addNum
	 * @param getDate
	 * @return String
	 * @Title: getDateAdded
	 */
	public static String getDateAdded(int addNum, String getDate) {
		return getCertainDate(getDate, addNum);
	}

	/**
	 * 比较两个时间是否为同一天
	 * 此处写方法描述
	 *
	 * @param oneTime
	 * @param otherTime
	 * @return boolean
	 * @Title: isEqualDay
	 */
	public static boolean isEqualDay(String oneTime, String otherTime) {
		Date oneDay = getDateFromString(oneTime, FORMAT_DATETIME);
		Date otherDay = getDateFromString(otherTime, FORMAT_DATETIME);
		String str1 = getStringFromDate(oneDay, FORMAT_DATE);
		String str2 = getStringFromDate(otherDay, FORMAT_DATE);
		return str1.equals(str2);
	}

	/**
	 * format string date
	 *
	 * @param time
	 * @param format
	 * @return
	 */
	public static String getStringByForFormat(String time, String format) {
		Date date = getDateFromString(time, format);
		return getStringFromDate(date, format);
	}

	/**
	 * format date
	 *
	 * @param d
	 * @param format
	 * @return String
	 * @Title: getStringFromDate
	 * @date 2014年6月17日 下午8:21:52
	 */
	public static String getStringFromDate(Date d, String format) {
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(d);
	}

	/**
	 * 将毫秒数转化为时间
	 *
	 * @param mills
	 * @return String
	 * @Title: MillsConvertDateStr
	 */
	public static String MillsConvertDateStr(long mills) {
		Date date = new Date(mills);
		SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_DATETIME);
		return sdf.format(date);
	}

	public static Date getDateFromString(String s, String format) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(format);
			return sdf.parse(s);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * add certainDays to get certainDate
	 *
	 * @param datetime certainDate
	 * @param days     add days
	 * @return
	 */
	public static String getCertainDate(String datetime, int days) {
		Date curDate = getDateFromString(datetime, FORMAT_DATE);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(curDate);
		calendar.add(Calendar.DATE, days);
		return getStringFromDate(calendar.getTime(), FORMAT_DATE);
	}

	/**
	 * 根据规定格式的字符串得到Calendar
	 *
	 * @param dateString
	 * @return Calendar
	 * @Title: getCalendar
	 */
	public static Calendar getCalendar(String dateString) {
		Calendar calendar = Calendar.getInstance();
		String[] items = dateString.split("-");
		calendar.set(Integer.parseInt(items[0]),
				Integer.parseInt(items[1]) - 1, Integer.parseInt(items[2]));
		return calendar;
	}

	/**
	 * 把 yyyy-MM-dd HH:mm:ss 格式的时间转换为毫秒。
	 *
	 * @param date
	 * @return long
	 * @throws ParseException
	 * @Title: DateConvertMills
	 */
	public static long DateConvertMills(String date) throws ParseException {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(FORMAT_DATETIME);
		return simpleDateFormat.parse(date).getTime();
	}

}
