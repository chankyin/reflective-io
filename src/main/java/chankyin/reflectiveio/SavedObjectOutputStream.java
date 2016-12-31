package chankyin.reflectiveio;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.NonNull;
import lombok.SneakyThrows;

import org.apache.commons.lang3.ArrayUtils;

import static chankyin.reflectiveio.ReflectiveIoUtils.DEBUG;

public class SavedObjectOutputStream extends FilterOutputStream{
	private final ByteOrder byteOrder;
	private final Map<String, Short> writtenVersions = new LinkedHashMap<>();

	public SavedObjectOutputStream(@NonNull OutputStream out){
		this(out, ByteOrder.BIG_ENDIAN);
	}

	public SavedObjectOutputStream(@NonNull OutputStream out, ByteOrder byteOrder){
		super(out);
		this.byteOrder = byteOrder;
	}

	public void writeByte(byte b){
		writeBytes(b);
	}

	public void writeShort(short s){
		writeBytes((byte) (s >>> 8), (byte) s);
	}

	public void writeInt(int i){
		writeBytes((byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i);
	}

	public void writeLong(long l){
		writeBytes((byte) (l >>> 56), (byte) (l >>> 48), (byte) (l >>> 40), (byte) (l >>> 32),
				(byte) (l >>> 24), (byte) (l >>> 16), (byte) (l >>> 8), (byte) l);
	}

	public void writeIntVarSize(long num, int size){
		byte[] bytes = new byte[size];
		for(int i = 0; i < size; i++){
			bytes[size - i - 1] = (byte) (num >>> i * 8);
		}
		writeBytes(bytes);
	}

	public void writeBoolean(boolean bool){
		writeByte((byte) (bool ? 1 : 0));
	}

	public void writeFloat(float f){
		writeInt(Float.floatToIntBits(f));
	}

	public void writeDouble(double d){
		writeLong(Double.doubleToLongBits(d));
	}

	public void writeChar(char c){
		writeShort((short) c);
	}

	public void writeString(String s){
		writeString(s, 3);
	}

	@SneakyThrows(IOException.class)
	public void writeString(String s, int size){
		byte[] bytes = s.getBytes();
		writeIntVarSize(bytes.length, size);
		write(bytes); // do not make it byte-order dependent!
	}

	public void writeSavedObject(Object object){
		if(object == null){
			writeBoolean(false);
			return;
		}
		if(DEBUG){
			System.err.println("Writing SavedObject " + object.getClass().getName());
		}
		writeBoolean(true);
		if(object.getClass().getDeclaredAnnotation(SavedObject.class) == null){
			throw new IllegalArgumentException("Cannot write non-@SavedObject");
		}
		writeHierarchyVersions(object.getClass());
		if(object instanceof Serialized){
			((Serialized) object).preSerialize();
		}
		ReflectiveIoUtils.getAllFields(object.getClass(), s -> s.getDeclaredAnnotation(SavedObject.class) != null)
				.stream().filter(field -> {
			SavedProperty annotation = field.getAnnotation(SavedProperty.class);
			return annotation != null && annotation.removed() == SavedProperty.VERSION_NIL;
		}).forEachOrdered(field -> writeField(field, object));
	}

	private void writeHierarchyVersions(Class<?> bottom){
		if(bottom.getDeclaredAnnotation(SavedObject.class) == null){
			throw new IllegalArgumentException("Cannot write hierarchy versions for non-@SavedObject");
		}
		for(Class<?> clazz = bottom; clazz != Object.class && clazz != null; clazz = clazz.getSuperclass()){
			SavedObject annotation = bottom.getDeclaredAnnotation(SavedObject.class);
			if(annotation == null){
				break;
			}
			writeBoolean(true);
			boolean alreadyWritten = writeVersion(clazz.getName(), annotation.value());
			if(alreadyWritten){
				return;
			}
		}
		writeBoolean(false);
	}

	private boolean writeVersion(String className, short version){
		if(writtenVersions.containsKey(className)){
			writeBoolean(true);
			writeShort((short) ReflectiveIoUtils.searchIndexInIterator(writtenVersions.keySet().iterator(), className));
			return true;
		}
		writtenVersions.put(className, version);
		writeBoolean(false);
		writeString(className);
		writeShort(version);
		return false;
	}

	@SneakyThrows({IllegalAccessException.class, ClassNotFoundException.class})
	private void writeField(Field field, Object instance){
		if(DEBUG){
			System.err.println(String.format("Writing field %s.%s", field.getDeclaringClass().getName(), field.getName()));
		}

		if(!field.isAccessible()){
			field.setAccessible(true);
		}
		Class<?> type = field.getType();
		if(byte.class.isAssignableFrom(type) || Byte.class.isAssignableFrom(type)){
			writeByte(field.getByte(instance));
		}else if(short.class.isAssignableFrom(type) || Short.class.isAssignableFrom(type)){
			writeShort(field.getShort(instance));
		}else if(int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)){
			writeInt(field.getInt(instance));
		}else if(long.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)){
			writeLong(field.getLong(instance));
		}else if(float.class.isAssignableFrom(type) || Float.class.isAssignableFrom(type)){
			writeFloat(field.getFloat(instance));
		}else if(double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)){
			writeDouble(field.getDouble(instance));
		}else if(boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)){
			writeBoolean(field.getBoolean(instance));
		}else if(char.class.isAssignableFrom(type) || Character.class.isAssignableFrom(type)){
			writeChar(field.getChar(instance));
		}else if(String.class.isAssignableFrom(type)){
			String string = (String) field.get(instance);
			if(string != null){
				writeBoolean(true);
				writeString(string);
			}else{
				writeBoolean(false);
			}
		}else if(Class.class.isAssignableFrom(type)){
			Class clazz = (Class) field.get(instance);
			if(clazz != null){
				writeBoolean(true);
				writeString(clazz.getName());
			}else{
				writeBoolean(false);
			}
		}else if(type.isEnum()){
			Enum anEnum = (Enum) field.get(instance);
			if(anEnum != null){
				writeBoolean(true);
				writeString(anEnum.name());
			}else{
				writeBoolean(false);
			}
		}else if(Collection.class.isAssignableFrom(type)){
			Type genericType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
			Class<?> classE = Class.forName(genericType.getTypeName());
			Collection coll = (Collection) field.get(instance);
			if(coll != null){
				writeBoolean(true);
				writeInt(coll.size());
				for(Object o : coll){
					writeDynType(classE, o);
				}
			}else{
				writeBoolean(false);
			}
		}else if(type.isArray()){
			Class<?> classComp = type.getComponentType();
			Object array = field.get(instance);
			if(array != null){
				writeBoolean(true);
				int length = Array.getLength(array);
				writeInt(length);
				for(int i = 0; i < length; i++){
					writeDynType(classComp, Array.get(array, i));
				}
			}else{
				writeBoolean(false);
			}
		}else if(Map.class.isAssignableFrom(type)){
			Type[] genericTypes = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
			Class<?> classK = Class.forName(genericTypes[0].getTypeName());
			Class<?> classV = Class.forName(genericTypes[1].getTypeName());
			Map map = (Map) field.get(instance);
			if(map != null){
				writeBoolean(true);
				writeInt(map.size());
				for(Object o : map.entrySet()){
					Map.Entry entry = (Map.Entry) o;
					writeDynType(classK, entry.getKey());
					writeDynType(classV, entry.getValue());
				}
			}else{
				writeBoolean(false);
			}
		}else if(type.getDeclaredAnnotation(SavedObject.class) != null){
			writeSavedObject(field.get(instance));
		}else{
			throw new UnsupportedOperationException("Cannot write type " + type.getName());
		}
	}

	private void writeDynType(Class<?> type, Object value){
		if(byte.class.isAssignableFrom(type) || Byte.class.isAssignableFrom(type)){
			writeByte((Byte) value);
		}else if(short.class.isAssignableFrom(type) || Short.class.isAssignableFrom(type)){
			writeShort((Short) value);
		}else if(int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)){
			writeInt((Integer) value);
		}else if(long.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)){
			writeLong((Long) value);
		}else if(float.class.isAssignableFrom(type) || Float.class.isAssignableFrom(type)){
			writeFloat((Float) value);
		}else if(double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)){
			writeDouble((Double) value);
		}else if(boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)){
			writeBoolean((Boolean) value);
		}else if(char.class.isAssignableFrom(type) || Character.class.isAssignableFrom(type)){
			writeChar((Character) value);
		}else if(String.class.isAssignableFrom(type)){
			writeString((String) value);
		}else if(Class.class.isAssignableFrom(type)){
			writeString(((Class) value).getName());
		}else if(type.isEnum()){
			writeString(((Enum) value).name());
		}else if(type.getDeclaredAnnotation(SavedObject.class) != null){
			writeSavedObject(value);
		}else if(type.isArray()){
			if(value != null){
				writeBoolean(true);
				Class<?> classComp = type.getComponentType();
				int length = Array.getLength(value);
				writeInt(length);
				for(int i = 0; i < length; i++){
					writeDynType(classComp, Array.get(value, i));
				}
			}else{
				writeBoolean(false);
			}
		}else{
			throw new UnsupportedOperationException("Cannot write parameterized type " + type.getName());
		}
	}

	/**
	 * Writes endianness-dependent bytes to the wrapped output stream
	 *
	 * @param bytes bytes in big endian
	 */
	@SneakyThrows(IOException.class)
	private void writeBytes(byte... bytes){
		if(byteOrder == ByteOrder.LITTLE_ENDIAN){
			ArrayUtils.reverse(bytes);
		}
		write(bytes);
	}
}
