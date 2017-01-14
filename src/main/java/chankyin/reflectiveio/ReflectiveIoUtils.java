package chankyin.reflectiveio;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;

public class ReflectiveIoUtils{
	static boolean DEBUG = false;

	public static ArrayList<Field> getAllFields(Class<?> clazz, Predicate<Class<?>> filter){
		return (ArrayList<Field>) getAllFields(clazz, new ArrayList<>(), filter);
	}

	/**
	 * Returns all fields of the class, including inherited fields and private fields from all superclasses
	 * <p>Warning: Do not change this method's return order. This may affect future versions.</p>
	 *
	 * @param clazz the class to get fields from
	 * @param coll  the collection to add fields to
	 * @return all fields of the class, including inherited fields and private fields from all superclasses
	 */
	public static Collection<Field> getAllFields(Class<?> clazz, Collection<Field> coll, Predicate<Class<?>> filter){
		if(clazz.getSuperclass() != Object.class && clazz.getSuperclass() != null && filter.test(clazz.getSuperclass())){
			getAllFields(clazz.getSuperclass(), coll, filter);
		}

		Collections.addAll(coll, clazz.getDeclaredFields());
		return coll;
	}

	public static <T> int searchIndexInIterator(Iterator<T> iterator, T value){
		return searchIndexInIterator(iterator, value, 0);
	}

	public static <T> int searchIndexInIterator(Iterator<T> iterator, T value, int startIndex){
		int expectedHashCode = value == null ? 0 : value.hashCode();
		for(int i = 0; i < startIndex && iterator.hasNext(); i++){
			iterator.next();
		}
		if(!iterator.hasNext()){
			return -1;
		}
		for(int i = startIndex; iterator.hasNext(); i++){
			T next = iterator.next();
			if(next == null){
				if(value == null){
					return i;
				}else{
					continue;
				}
			}else if(value == null){
				continue;
			}

			if(next.hashCode() == expectedHashCode && next.equals(value)){
				return i;
			}
		}
		return -1;
	}

	public static <T> T getIndexInIterator(Iterator<T> iterator, int index){
		for(int i = 0; iterator.hasNext(); i++){
			T current = iterator.next();
			if(i == index){
				return current;
			}
		}
		return null;
	}
}
