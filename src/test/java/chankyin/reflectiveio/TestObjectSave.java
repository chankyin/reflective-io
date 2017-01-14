package chankyin.reflectiveio;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.Cleanup;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.junit.Assert;
import org.junit.Test;

@EqualsAndHashCode
@ToString
public class TestObjectSave{
	static{
		ReflectiveIoUtils.DEBUG = true;
	}

	@SavedProperty(1)
	private String testObjectSave = "a";

	final File fileForTest = new File(".", "Bar.dat");

	@SavedObject(2)
	@NoArgsConstructor
	@EqualsAndHashCode(callSuper = true)
	@ToString(callSuper = true)
	public static class Foo extends TestObjectSave{
		@SavedProperty(2) private String foo = "b";
		private int i = 0xdeadbeef;

		@SavedProperty(2) private Content c0 = new Content(this);
	}

	@SavedObject(3)
	@NoArgsConstructor
	@EqualsAndHashCode(callSuper = true)
	@ToString(callSuper = true)
	public static class Bar extends Foo{
		@SavedProperty(value = 2, removed = 3) private String bar = "c";
		@SavedProperty(3) private long i = 0xd3adc0debeeffaceL;
		// @SavedProperty(1) private Content c1 = new Content(this);
	}

	@SavedObject(3)
	@EqualsAndHashCode()
	public static class Content{
		private static int counter = 0;

		@FillWithOwner public Foo bar;

		@SavedProperty(2) private List<Float> floatList0;
		@SavedProperty(2) private List<Float> floatList1;

		public Content(Foo owner){
			this();
			bar = owner;
		}

		public Content(){
			if(counter++ == 0){
				floatList0 = Arrays.asList(Float.POSITIVE_INFINITY, Float.MIN_VALUE);
				floatList0 = Collections.singletonList(Float.NaN);
			}else{
				floatList1 = Arrays.asList(Float.NEGATIVE_INFINITY, Float.MAX_VALUE);
				floatList1 = Collections.singletonList(Float.MIN_NORMAL);
			}
		}

		@Override
		public String toString(){
			return "TestObjectSave.Content(floatList0=" + floatList0 + ", floatList1=" + floatList1 + ", " +
					"bar=" + (bar == null ? "null" : bar.getClass()) + ")";
		}
	}

	@Test
	public void doJunitWarmup() throws IOException{
		Assert.assertNotNull(SavedObjectOutputStream.class);
		Assert.assertNotNull(SavedObjectInputStream.class);
		fileForTest.createNewFile();
		fileForTest.delete();
	}

	@Test
	public void doTestWrite() throws Exception{
		testWrite0(new Bar());
		fileForTest.delete(); // if no exceptions
	}

	@Test
	public void doTestRead() throws Exception{
		Bar bar = new Bar();
		testWrite0(bar);
		Object object = testRead0();
		System.err.println("EXPECT: " + bar.toString());
		System.err.println("PARSED: " + object.toString());
		Assert.assertEquals(bar.toString(), object.toString());
		fileForTest.delete(); // if no exceptions
	}

	public void testWrite0(Object object) throws Exception{
		@Cleanup OutputStream os = new FileOutputStream(fileForTest);
		SavedObjectOutputStream soos = new SavedObjectOutputStream(os);
		soos.writeSavedObject(object);
	}

	public Object testRead0() throws Exception{
		@Cleanup InputStream is = new FileInputStream(fileForTest);
		SavedObjectInputStream sois = new SavedObjectInputStream(is);
		return sois.readSavedObject(null);
	}
}
