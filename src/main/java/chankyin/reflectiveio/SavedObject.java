package chankyin.reflectiveio;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import lombok.NoArgsConstructor;

/**
 * Warning: Should be used with {@link NoArgsConstructor}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SavedObject{
	short value();
}
