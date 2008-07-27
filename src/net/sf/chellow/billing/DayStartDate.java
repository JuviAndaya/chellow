package net.sf.chellow.billing;

import java.util.Calendar;
import java.util.Date;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.sf.chellow.monad.HttpException;
import net.sf.chellow.monad.InternalException;
import net.sf.chellow.monad.UserException;

import net.sf.chellow.monad.types.MonadDay;
import net.sf.chellow.monad.types.MonadMonth;
import net.sf.chellow.physical.HhEndDate;

public class DayStartDate extends HhEndDate {
	public static DayStartDate getNext(DayStartDate date) throws HttpException {
		return new DayStartDate(new Date(getNext(getCalendar(), date.getDate()
				.getTime())));
	}

	public static DayStartDate getPrevious(DayStartDate date)
			throws HttpException {
		return new DayStartDate(new Date(getPrevious(getCalendar(), date
				.getDate().getTime())));
	}

	/*
	 * public static DayEndDate roundUp(Date date) throws ProgrammerException,
	 * UserException { return new DayEndDate(new Date(roundUp(getCalendar(),
	 * date.getTime()))); }
	 */
	public static long getNext(Calendar cal, long date) {
		return roundUp(cal, date + 1);
	}

	public static DayStartDate roundUp(Date date) throws HttpException {
		return new DayStartDate(
				new Date(roundUp(getCalendar(), date.getTime())));
	}

	protected static long roundUp(Calendar cal, long date) {
		HhEndDate.roundUp(cal, date);
		if (cal.get(Calendar.MINUTE) == 0) {
			cal.add(Calendar.MINUTE, 30);
		}
		if (cal.get(Calendar.HOUR_OF_DAY) != 0) {
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.add(Calendar.DAY_OF_MONTH, 1);
		}
		return cal.getTimeInMillis();
	}

	public static DayStartDate roundDown(Date date) throws HttpException {
		return new DayStartDate(new Date(roundDown(getCalendar(), date
				.getTime())));
	}

	public static long getPrevious(Calendar cal, long date) {
		return roundDown(cal, date - 1);
	}

	public static long roundDown(Calendar cal, long date) {
		HhEndDate.roundDown(cal, date);
		if (cal.get(Calendar.MINUTE) == 0) {
			cal.set(Calendar.MINUTE, 30);
			cal.add(Calendar.HOUR_OF_DAY, -1);
		}
		if (cal.get(Calendar.HOUR_OF_DAY) != 0) {
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.add(Calendar.DAY_OF_MONTH, -1);
		}
		return cal.getTimeInMillis();
	}

	DayStartDate() throws HttpException {
		super(DayStartDate.roundUp(new Date()).getDate());
	}

	public DayStartDate(Date date) throws HttpException {
		super(date);
	}

	public DayStartDate(String label, String year, String month, String day)
			throws HttpException {
		super(label, year, month, day);
		setTypeName();
	}

	public DayStartDate(int year, MonadMonth month, MonadDay day)
			throws HttpException {
		super(year, month, day);
	}

	public DayStartDate(String dateStr) throws HttpException {
		super(dateStr);
	}

	public DayStartDate(String label, String dateStr) throws HttpException {
		super(label, dateStr);
	}

	private void setTypeName() {
		setTypeName();
	}

	public void update(Date date) throws HttpException {
		super.update(date);
		Calendar cal = getCalendar();
		cal.clear();
		cal.setTime(date);
		if (cal.get(Calendar.HOUR_OF_DAY) != 0) {
			throw new UserException("For the date " + date
					+ ", the hours must be 0.");
		}
		if (cal.get(Calendar.MINUTE) == 0) {
			throw new UserException("For the date " + date
					+ ", the minutes must be 30.");
		}
		super.update(cal.getTime());
	}

	public DayStartDate getPrevious() throws HttpException {
		return getPrevious(this);
	}

	public DayStartDate getNext() throws HttpException {
		return getNext(this);
	}

	public Element toXml(Document doc) throws InternalException {
		return toXML(getDate(), getLabel(), doc, "day-start-date");
	}
}
