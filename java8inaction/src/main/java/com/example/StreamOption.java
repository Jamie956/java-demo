package com.example;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StreamOption {
	public static void main(String[] args) {
		test09();
	}
	
	public static void test01() {
		Stream<String> stream = Stream.of("a", "b", "c", "a");
		// Stream filter
		stream = stream.filter((x) -> x == "a" );
		stream.forEach(System.out::println);
	}

	public static void test02() {
		Stream<String> stream = Stream.of("a", "b", "c", "a");
		// Stream distinct
		stream = stream.distinct();
		stream.forEach(System.out::println);
	}

	public static void test03() {
		Stream<String> stream = Stream.of("a", "b", "c", "a");
		// Stream skip
		stream = stream.skip(2);
		stream.forEach(System.out::println);
	}
	
	public static void test04() {
		Stream<String> stream = Stream.of("a", "b", "c", "a");
		// Stream sorted
		stream = stream.sorted((a, b) -> a.compareTo(b));
		stream.forEach(System.out::println);
	}
	
	public static void test05() {
		Stream<String> stream = Stream.of("s", "b", "c");
		// String map
		stream.map(String::toUpperCase).limit(2).forEach(System.out::println);
	}
	
	public static void test06() {
		// IntStream
		IntStream stream = IntStream.of(1, 2, 3);
		System.out.println(stream.sum());
	}

	public static void test07() {
		//Stream iterate
		Stream.iterate(0, n -> n + 2).limit(10).forEach(System.out::println);
	}

	public static void test08() {
		// generate
		Stream.generate(Math::random).limit(10).forEach(System.out::println);
	}
	
	public static void test09() {
		Stream<String> stream = Stream.of("s", "b", "c");
		//allMatch
//		System.out.println(stream.allMatch(x -> x == "s"));
		//anyMatch
		System.out.println(stream.anyMatch(x -> x == "s"));
	}
	
	public static void test10() {
		IntStream intStream = IntStream.of(1, 2, 3);
		// reduce
		System.out.println(intStream.reduce(0, (a, b) -> a + b));
	}
}
