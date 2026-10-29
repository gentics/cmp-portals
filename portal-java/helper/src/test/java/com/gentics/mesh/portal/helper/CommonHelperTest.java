package com.gentics.mesh.portal.helper;

import static com.gentics.mesh.portal.util.StreamUtil.fromIterable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.gentics.mesh.portal.config.PortalConfig;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.helper.EachHelper;

import io.vertx.core.json.JsonArray;

public class CommonHelperTest {
	private CommonHelper commonHelper;
	private Options options;
	private PortalConfig config;
	private EachHelper eachHelper;

	@Before
	public void setUp() throws Exception {
		options = mock(Options.class);
		config = mock(PortalConfig.class);
		eachHelper = mock(EachHelper.class);
		commonHelper = new CommonHelper(config, eachHelper);
	}

	@Test
	public void testArraySize() {
		assertThat(commonHelper.getArraySize(new JsonArray())).isEqualTo("0");
		assertThat(commonHelper.getArraySize(new JsonArray(Arrays.asList(1, 2, 3)))).isEqualTo("3");
		assertThat(commonHelper.getArraySize(null)).isEqualTo("0");
	}

	@Test
	public void testEachReversed() throws IOException {
		commonHelper.eachReversed(new JsonArray(), options);
		verify(eachHelper).apply(argThat(hasItemCount(0)), eq(options));

		commonHelper.eachReversed(new JsonArray(Arrays.asList(1, 2, 3)), options);
		verify(eachHelper).apply(argThat(hasItems(3, 2, 1)), eq(options));

		commonHelper.eachReversed(null, options);
		verify(eachHelper).apply(isNull(), eq(options));
	}

	private ArgumentMatcher<Iterable<?>> hasItemCount(long count) {
		return iterable -> fromIterable(iterable).count() == count;
	}

	private ArgumentMatcher<Iterable<?>> hasItems(Object... items) {
		return iterable -> Arrays.equals(
			fromIterable(iterable).toArray(),
			items
		);
	}
}
