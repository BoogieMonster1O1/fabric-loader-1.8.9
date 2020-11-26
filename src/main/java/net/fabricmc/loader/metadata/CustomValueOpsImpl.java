package net.fabricmc.loader.metadata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.CustomValueOps;

public enum CustomValueOpsImpl implements CustomValueOps {
	INSTANCE;

	@Override
	public CustomValue empty() {
		return CustomValueImpl.NULL;
	}

	@Override
	public CustomValue emptyMap() {
		return this.createMap(Collections.emptyMap());
	}

	@Override
	public CustomValue emptyList() {
		return this.createList(Stream.empty());
	}

	@Override
	public <U> U convertTo(DynamicOps<U> outOps, CustomValue input) {
		if (input.getType() == CustomValue.CvType.OBJECT) {
			return this.convertMap(outOps, input);
		} else if (input.getType() == CustomValue.CvType.ARRAY) {
			return this.convertList(outOps, input);
		} else if (input.getType() == CustomValue.CvType.NULL) {
			return outOps.empty();
		} else if (input.getType() == CustomValue.CvType.STRING) {
			return outOps.createString(input.getAsString());
		} else if (input.getType() == CustomValue.CvType.BOOLEAN) {
			return outOps.createBoolean(input.getAsBoolean());
		}
		BigDecimal bigDecimal = BigDecimal.valueOf(input.getAsNumber().doubleValue());
		try {
			long l = bigDecimal.longValueExact();
			if ((byte) l == l) {
				return outOps.createByte((byte) l);
			} else if ((short) l == l) {
				return outOps.createShort((short) l);
			} else if ((int) l == l) {
				return outOps.createInt((int) l);
			}
			return outOps.createLong(l);
		} catch (ArithmeticException e) {
			double d = bigDecimal.doubleValue();
			if ((float) d == d) {
				return outOps.createFloat((float) d);
			}
			return outOps.createDouble(d);
		}
	}

	@Override
	public DataResult<Number> getNumberValue(CustomValue input) {
		if (input.getType() != CustomValue.CvType.NUMBER) {
			return DataResult.error("Not a number: " + input);
		}
		return DataResult.success(input.getAsNumber());
	}

	@Override
	public CustomValue createNumeric(Number i) {
		return new CustomValueImpl.NumberImpl(i);
	}

	@Override
	public DataResult<Boolean> getBooleanValue(CustomValue input) {
		if (input == CustomValueImpl.BOOLEAN_TRUE) {
			return DataResult.success(Boolean.TRUE);
		} else if (input == CustomValueImpl.BOOLEAN_FALSE) {
			return DataResult.success(Boolean.FALSE);
		}
		return DataResult.error("Not a boolean: " + input);
	}

	@Override
	public CustomValue createBoolean(boolean value) {
		return value ? CustomValueImpl.BOOLEAN_TRUE : CustomValueImpl.BOOLEAN_FALSE;
	}

	@Override
	public DataResult<String> getStringValue(CustomValue input) {
		if (input.getType() != CustomValue.CvType.STRING) {
			return DataResult.error("Not a string: " + input);
		}
		return DataResult.success(input.getAsString());
	}

	@Override
	public CustomValue createString(String value) {
		return new CustomValueImpl.StringImpl(value);
	}

	@Override
	public DataResult<CustomValue> mergeToList(CustomValue list, CustomValue value) {
		if (list.getType() != CustomValue.CvType.ARRAY) {
			return DataResult.error("Not an array: " + list);
		}
		List<CustomValue> values = this.stream(list.getAsArray()).collect(Collectors.toList());
		values.add(value);
		return DataResult.success(this.createList(values.stream()));
	}

	@Override
	public DataResult<CustomValue> mergeToMap(CustomValue map, CustomValue key, CustomValue value) {
		if (map.getType() != CustomValue.CvType.OBJECT) {
			return DataResult.error("Map is not an object: " + map);
		} else if (key.getType() != CustomValue.CvType.STRING) {
			return DataResult.error("Key is not a string: " + key);
		}
		Map<CustomValue, CustomValue> valueMap = this.stream(map.getAsObject()).collect(Collectors.toMap(entry -> this.createString(entry.getKey()), Map.Entry::getValue));
		valueMap.put(key, value);
		return DataResult.success(this.createMap(valueMap));
	}

	@Override
	public DataResult<CustomValue> mergeToMap(CustomValue map, Map<CustomValue, CustomValue> values) {
		return this.mergeToMap(map, MapLike.forMap(values, this));
	}

	@Override
	public DataResult<CustomValue> mergeToMap(CustomValue map, MapLike<CustomValue> values) {
		if (map.getType() != CustomValue.CvType.OBJECT) {
			return DataResult.error("Map is not an object: " + map);
		}
		Map<CustomValue, CustomValue> valueMap = StreamSupport.stream(map.getAsObject().spliterator(), false).collect(Collectors.toMap(entry -> this.createString(entry.getKey()), Map.Entry::getValue));
		List<CustomValue> missed = new ArrayList<>();
		values.entries().forEach(pair -> {
			if (pair.getFirst().getType() != CustomValue.CvType.STRING) {
				missed.add(pair.getFirst());
				return;
			}
			valueMap.put(pair.getFirst(), pair.getSecond());
		});
		if (!missed.isEmpty()) {
			return DataResult.error("Keys are not strings: " + missed, this.createMap(valueMap));
		}
		return DataResult.success(this.createMap(valueMap));
	}

	@Override
	public DataResult<Stream<Pair<CustomValue, CustomValue>>> getMapValues(CustomValue input) {
		if (input.getType() != CustomValue.CvType.OBJECT) {
			return DataResult.error("Not an object: " + input);
		}
		return DataResult.success(StreamSupport.stream(input.getAsObject().spliterator(), false).map(entry -> Pair.of(this.createString(entry.getKey()), entry.getValue())));
	}

	@Override
	public CustomValue.CvObject createMap(Map<CustomValue, CustomValue> map) {
		Map<String, CustomValue> stringKeyMap = map.entrySet().stream().map(entry -> {
			if (entry.getKey().getType() != CustomValue.CvType.STRING) {
				throw new IllegalArgumentException("Not a string: " + entry.getKey());
			}
			return Pair.of(entry.getKey().getAsString(), entry.getValue());
		}).collect(Collectors.<Pair<String, CustomValue>, String, CustomValue>toMap(Pair::getFirst, Pair::getSecond));
		return new CustomValueImpl.ObjectImpl(stringKeyMap);
	}

	@Override
	public CustomValue.CvObject createMap(Stream<Pair<CustomValue, CustomValue>> map) {
		return this.createMap(map.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)));
	}

	@Override
	public DataResult<Stream<CustomValue>> getStream(CustomValue input) {
		if (input.getType() != CustomValue.CvType.ARRAY) {
			return DataResult.error("Not an array: " + input.toString());
		}
		return DataResult.success(this.stream(input.getAsArray()));
	}

	@Override
	public CustomValue.CvArray createList(Stream<CustomValue> input) {
		return new CustomValueImpl.ArrayImpl(input.collect(Collectors.toList()));
	}

	@Override
	public CustomValue remove(CustomValue input, String key) {
		if (input.getType() != CustomValue.CvType.OBJECT) {
			return input;
		}
		return this.createMap(this.stream(input.getAsObject()).filter(entry -> Objects.equals(entry.getKey(), key)).map(entry -> Pair.of(this.createString(entry.getKey()), entry.getValue())));
	}

	public Stream<Map.Entry<String, CustomValue>> stream(CustomValue.CvObject object) {
		return StreamSupport.stream(object.spliterator(), false);
	}

	public Stream<CustomValue> stream(CustomValue.CvArray object) {
		return StreamSupport.stream(object.spliterator(), false);
	}
}
