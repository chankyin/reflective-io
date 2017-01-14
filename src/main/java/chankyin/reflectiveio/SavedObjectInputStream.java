package chankyin.reflectiveio;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.NonNull;
import lombok.SneakyThrows;

import static chankyin.reflectiveio.ReflectiveIoUtils.DEBUG;

public class SavedObjectInputStream extends FilterInputStream{
	private final ByteOrder byteOrder;
	private final Map<String, Short> readVersions = new LinkedHashMap<>();

	public SavedObjectInputStream(@NonNull InputStream in){
		this(in, ByteOrder.BIG_ENDIAN);
	}

	public SavedObjectInputStream(@NonNull InputStream in, ByteOrder byteOrder){
		super(in);
		this.byteOrder = byteOrder;
	}

	public byte readByte(){
		return readBytes(1)[0];
	}

	public short readShort(){
		byte[] bytes = readBytes(2);
		return (short) ((bytes[0] & 0xFF) << 8 | bytes[1] & 0xFF);
	}

	public int readInt(){
		byte[] bytes = readBytes(4);
		return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | bytes[3] & 0xFF;
	}

	public long readLong(){
		byte[] bytes = readBytes(8);
		return (long) (bytes[0] & 0xFF) << 56 | (long) (bytes[1] & 0xFF) << 48 |
				(long) (bytes[2] & 0xFF) << 40 | (long) (bytes[3] & 0xFF) << 32 |
				(long) (bytes[4] & 0xFF) << 24 | (bytes[5] & 0xFF) << 16 |
				(bytes[6] & 0xFF) << 8 | bytes[7] & 0xFF;
	}

	public long readIntVarSize(int size){
		byte[] bytes = readBytes(size);
		long output = 0L;
		for(int i = 0; i < size; i++){
			output |= (long) (bytes[size - i - 1] & 0xFF) << i * 8;
		}
		return output;
	}

	public boolean readBoolean(){
		return readByte() != 0;
	}

	public float readFloat(){
		return Float.intBitsToFloat(readInt());
	}

	public double readDouble(){
		return Double.longBitsToDouble(readLong());
	}

	public char readChar(){
		return (char) readShort();
	}

	public String readString(){
		return readString(3);
	}

	@SneakyThrows(IOException.class)
	public String readString(int size){
		int l = (int) readIntVarSize(size);
		byte[] bytes = new byte[l];
		read(bytes);
		return new String(bytes);
	}

	@SneakyThrows({IllegalAccessException.class, InstantiationException.class})
	public Object readSavedObject(Object owningObject){
		if(!readBoolean()){
			return null;
		}
		String className = readHierarchyVersions();
		Class<?> clazz;
		try{
			clazz = Class.forName(className);
		}catch(ClassNotFoundException e){
			throw new ClassCastException(className);
		}
		if(DEBUG){
			System.err.println(String.format("Reading SavedObject %s (%s)", clazz.getName(), className));
		}
		Object object = clazz.newInstance();

		if(owningObject != null){
			for(Field field : ReflectiveIoUtils.getAllFields(clazz, s -> s.getDeclaredAnnotation(FillWithOwner.class) != null)){
				if(field.getType().isInstance(owningObject)){ // a class may have multiple possible owners
					if(!field.isAccessible()) field.setAccessible(true);
					field.set(object, owningObject);
				}
			}
		}
		for(Field field : ReflectiveIoUtils.getAllFields(clazz, s -> s.getDeclaredAnnotation(SavedObject.class) != null)){
			if(DEBUG){
				System.err.println(readVersions);
				System.err.println(field.getDeclaringClass().getName());
				System.err.println(readVersions.get(field.getDeclaringClass().getName()));
			}
			short savedVersion = readVersions.get(field.getDeclaringClass().getName());
			SavedProperty fieldAnnotation = field.getAnnotation(SavedProperty.class);
			if(fieldAnnotation != null && fieldAnnotation.value() <= savedVersion &&
					(savedVersion < fieldAnnotation.removed() || fieldAnnotation.removed() == SavedProperty.VERSION_NIL)){
				readField(field, object);
			}
		}

		if(object instanceof Unserialized){
			((Unserialized) object).postUnserialize();
		}

		return object;
	}

	@SneakyThrows({IOException.class})
	private String readHierarchyVersions(){
		String ret = null;
		while(readBoolean()){
			boolean alreadyWritten = readBoolean();
			if(alreadyWritten){
				short index = readShort();
				if(ret == null){
					ret = ReflectiveIoUtils.getIndexInIterator(readVersions.keySet().iterator(), index);
				}
				break;
			}else{
				String current = readString();
				short version = readShort();
				readVersions.put(current, version);
				if(ret == null){
					ret = current;
				}
			}
		}
		if(ret == null){
			throw new IOException();
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	@SneakyThrows({IllegalAccessException.class, ClassNotFoundException.class})
	private void readField(Field field, Object instance){
		if(!field.isAccessible()){
			field.setAccessible(true);
		}
		if(DEBUG){
			System.err.println(String.format("Reading field %s.%s", field.getDeclaringClass().getName(), field.getName()));
		}
		Class<?> type = field.getType();
		if(byte.class.isAssignableFrom(type)){
			field.setByte(instance, readByte());
		}else if(short.class.isAssignableFrom(type)){
			field.setShort(instance, readShort());
		}else if(int.class.isAssignableFrom(type)){
			field.setInt(instance, readInt());
		}else if(long.class.isAssignableFrom(type)){
			field.setLong(instance, readLong());
		}else if(float.class.isAssignableFrom(type)){
			field.setFloat(instance, readFloat());
		}else if(double.class.isAssignableFrom(type)){
			field.setDouble(instance, readDouble());
		}else if(boolean.class.isAssignableFrom(type)){
			field.setBoolean(instance, readBoolean());
		}else if(char.class.isAssignableFrom(type)){
			field.setChar(instance, readChar());
		}else if(String.class.isAssignableFrom(type)){
			field.set(instance, readBoolean() ? readString() : null);
		}else if(Class.class.isAssignableFrom(type)){
			field.set(instance, readBoolean() ? Class.forName(readString()) : null);
		}else if(type.isEnum()){
			field.set(instance, readBoolean() ? Enum.valueOf(type.asSubclass(Enum.class), readString()) : null);
		}else if(Collection.class.isAssignableFrom(type)){
			if(readBoolean()){
				int length = readInt();
				Type genericType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
				Class<?> classE = Class.forName(genericType.getTypeName());
				Collection coll = new ArrayList(length);
				for(int i = 0; i < length; i++){
					coll.add(readDynType(classE, instance));
				}
				field.set(instance, coll);
			}else{
				field.set(instance, null);
			}
		}else if(type.isArray()){
			if(readBoolean()){
				int length = readInt();
				Class<?> classComp = type.getComponentType();
				Object array = Array.newInstance(classComp);
				for(int i = 0; i < length; i++){
					Array.set(array, i, readDynType(classComp, instance));
				}
				field.set(instance, array);
			}else{
				field.set(instance, null);
			}
		}else if(Map.class.isAssignableFrom(type)){
			if(readBoolean()){
				Type[] genericTypes = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
				Class<?> classK = Class.forName(genericTypes[0].getTypeName());
				Class<?> classV = Class.forName(genericTypes[1].getTypeName());

				int length = readInt();
				Map map = new LinkedHashMap(length);
				for(int i = 0; i < length; i++){
					Object k = readDynType(classK, instance);
					Object v = readDynType(classV, instance);
				}
				field.set(instance, map);
			}else{
				field.set(instance, null);
			}
		}else if(type.getDeclaredAnnotation(SavedObject.class) != null){
			field.set(instance, readSavedObject(instance));
		}else{
			throw new UnsupportedOperationException("Cannot write type " + type.getName());
		}
	}

	@SneakyThrows(ClassNotFoundException.class)
	private Object readDynType(Class<?> type, Object owningObject){
		if(byte.class.isAssignableFrom(type) || Byte.class.isAssignableFrom(type)){
			return readByte();
		}else if(short.class.isAssignableFrom(type) || Short.class.isAssignableFrom(type)){
			return readShort();
		}else if(int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)){
			return readInt();
		}else if(long.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)){
			return readLong();
		}else if(float.class.isAssignableFrom(type) || Float.class.isAssignableFrom(type)){
			return readFloat();
		}else if(double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)){
			return readDouble();
		}else if(boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)){
			return readBoolean();
		}else if(char.class.isAssignableFrom(type) || Character.class.isAssignableFrom(type)){
			return readChar();
		}else if(String.class.isAssignableFrom(type)){
			return readString();
		}else if(Class.class.isAssignableFrom(type)){
			return Class.forName(readString());
		}else if(type.isEnum()){
			return Enum.valueOf(type.asSubclass(Enum.class), readString());
		}else if(type.getDeclaredAnnotation(SavedObject.class) != null){
			return readSavedObject(owningObject);
		}else if(type.isArray()){
			if(!readBoolean()){
				return null;
			}
			int length = readInt();
			Class<?> classComp = type.getComponentType();
			Object array = Array.newInstance(classComp);
			for(int i = 0; i < length; i++){
				Array.set(array, i, readDynType(classComp, owningObject));
			}
			return array;
		}else{
			throw new UnsupportedOperationException("Cannot read parameterized type " + type.getName());
		}
	}

	/**
	 * Read bytes from the wrapped stream, and flip them if byte order is small-endian.
	 *
	 * @return Big-endian bytes
	 */
	@SneakyThrows(IOException.class)
	private byte[] readBytes(int size){
		byte[] bytes = new byte[size];
		read(bytes);
		return bytes;
	}
}
