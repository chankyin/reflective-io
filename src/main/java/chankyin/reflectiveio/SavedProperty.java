package chankyin.reflectiveio;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SavedProperty{
	public final static short VERSION_NIL = 32767;

	/**
	 * Returns the version ID of the database version that added this property.
	 *
	 * @return the version ID of the database version that added this property
	 */
	short value();

	/**
	 * Returns the version ID of the database version that removed this property , i.e. the earliest version after
	 * adding this property that this property does not exist.
	 *
	 * @return the version ID of the database version that removed this property
	 */
	short removed() default VERSION_NIL;
}
