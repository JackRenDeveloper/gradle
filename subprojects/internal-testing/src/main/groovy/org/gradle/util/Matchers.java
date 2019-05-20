/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.util;

import org.gradle.internal.SystemProperties;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.equalTo;

public class Matchers {

    @Factory
    public static <T extends CharSequence> Matcher<T> matchesRegexp(final String pattern) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return Pattern.compile(pattern, Pattern.DOTALL).matcher((CharSequence) o).matches();
            }

            public void describeTo(Description description) {
                description.appendText("a CharSequence that matches regexp ").appendValue(pattern);
            }
        };
    }

    @Factory
    public static <T extends CharSequence> Matcher<T> matchesRegexp(final Pattern pattern) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return pattern.matcher((CharSequence) o).matches();
            }

            public void describeTo(Description description) {
                description.appendText("a CharSequence that matches regexp ").appendValue(pattern);
            }
        };
    }

    @Factory
    public static <T extends CharSequence> Matcher<T> containsText(final String pattern) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return ((String) o).contains(pattern);
            }
            public void describeTo(Description description) {
                description.appendText("a CharSequence that contains text ").appendValue(pattern);
            }
        };
    }

    @Factory
    public static <T> Matcher<T> strictlyEqual(final T other) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return strictlyEquals(o, other);
            }

            public void describeTo(Description description) {
                description.appendText("an Object that strictly equals ").appendValue(other);
            }
        };
    }

    public static boolean strictlyEquals(Object a, Object b) {
        if (!a.equals(b)) {
            return false;
        }
        if (!b.equals(a)) {
            return false;
        }
        if (!a.equals(a)) {
            return false;
        }
        if (b.equals(null)) {
            return false;
        }
        if (b.equals(new Object())) {
            return false;
        }
        if (a.hashCode() != b.hashCode()) {
            return false;
        }
        return true;

    }

    @Factory
    @Deprecated
    /**
     * Please avoid using as the hamcrest way of reporting error wraps a multi-line
     * text into a single line and makes hard to understand the problem.
     * Instead, please try to use the spock/groovy assert and {@link #containsLine(String, String)}
     */
    public static Matcher<String> containsLine(final String line) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                return containsLine(equalTo(line)).matches(o);
            }

            public void describeTo(Description description) {
                description.appendText("a String that contains line ").appendValue(line);
            }
        };
    }

    @Factory
    public static Matcher<String> containsLine(final Matcher<? super String> matcher) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                String str = (String) o;
                return containsLine(str, matcher);
            }

            public void describeTo(Description description) {
                description.appendText("a String that contains line that is ").appendDescriptionOf(matcher);
            }
        };
    }

    public static boolean containsLine(String action, String expected) {
        return containsLine(action, equalTo(expected));
    }

    public static boolean containsLine(String actual, Matcher<? super String> matcher) {
        BufferedReader reader = new BufferedReader(new StringReader(actual));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (matcher.matches(line)) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Factory
    public static Matcher<Iterable<?>> isEmpty() {
        return new BaseMatcher<Iterable<?>>() {
            public boolean matches(Object o) {
                Iterable<?> iterable = (Iterable<?>) o;
                return iterable != null && !iterable.iterator().hasNext();
            }

            public void describeTo(Description description) {
                description.appendText("an empty Iterable");
            }
        };
    }

    @Factory
    public static Matcher<Object> isSerializable() {
        return new BaseMatcher<Object>() {
            public boolean matches(Object o) {
                try {
                    new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(o);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }

            public void describeTo(Description description) {
                description.appendText("is serializable");
            }
        };
    }

    @Factory
    public static Matcher<Throwable> hasMessage(final Matcher<String> matcher) {
        return new BaseMatcher<Throwable>() {
            public boolean matches(Object o) {
                Throwable t = (Throwable) o;
                return matcher.matches(t.getMessage());
            }

            public void describeTo(Description description) {
                description.appendText("an exception with message that is ").appendDescriptionOf(matcher);
            }
        };
    }

    @Factory
    public static Matcher<String> normalizedLineSeparators(final Matcher<String> matcher) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                String string = (String) o;
                return matcher.matches(string.replace(SystemProperties.getInstance().getLineSeparator(), "\n"));
            }

            public void describeTo(Description description) {
                matcher.describeTo(description);
                description.appendText(" (normalize line separators)");
            }
        };
    }

}
