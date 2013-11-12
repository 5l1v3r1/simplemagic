package com.j256.simplemagic.entries;

import java.util.ArrayList;
import java.util.List;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.endian.EndianConverter;

/**
 * Representation of a line of information from the magic (5) format. A number of methods are package protected because
 * this is generated by the {@link MagicEntryParser}.
 * 
 * @author graywatson
 */
public class MagicEntry {

	private static final String UNKNOWN_NAME = "unknown";

	// list is used while parsing entries, array is used while matching on them 
	private List<MagicEntry> childrenList;
	private MagicEntry[] childrenArray;

	private final MagicEntry parent;
	private final String name;
	private final int level;
	private final boolean addOffset;
	private final int offset;
	private final OffsetInfo offsetInfo;
	private final MagicMatcher matcher;
	private final Long andValue;
	private final boolean unsignedType;
	// the testValue object is defined by the particular matcher
	private final Object testValue;
	private final boolean formatSpacePrefix;
	private final MagicFormatter formatter;

	private int strength;
	private String mimeType;

	/**
	 * Package protected constructor.
	 */
	MagicEntry(MagicEntry parent, String name, int level, boolean addOffset, int offset, OffsetInfo offsetInfo,
			MagicMatcher matcher, Long andValue, boolean unsignedType, Object testValue, boolean formatSpacePrefix,
			String format) {
		this.parent = parent;
		this.name = name;
		this.level = level;
		this.addOffset = addOffset;
		this.offset = offset;
		this.offsetInfo = offsetInfo;
		this.matcher = matcher;
		this.andValue = andValue;
		this.unsignedType = unsignedType;
		this.testValue = testValue;
		this.formatSpacePrefix = formatSpacePrefix;
		if (format == null) {
			this.formatter = null;
		} else {
			this.formatter = new MagicFormatter(format);
		}
		this.strength = 1;
	}

	/**
	 * Returns the content type associated with the bytes or null if it does not match.
	 */
	public ContentInfo processBytes(byte[] bytes) {
		ContentData data = processBytes(bytes, 0, null);
		if (data == null || data.name == UNKNOWN_NAME) {
			return null;
		} else {
			return new ContentInfo(data.name, data.mimeType, data.sb.toString(), data.partial);
		}
	}

	/**
	 * Return the "level" of the rule. Level-0 rules start the matching process. Level-1 and above rules are processed
	 * only when the level-0 matches.
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Get the strength of the rule. Not well supported right now.
	 */
	public int getStrength() {
		return strength;
	}

	void setStrength(int strength) {
		this.strength = strength;
	}

	MagicEntry getParent() {
		return parent;
	}

	void addChild(MagicEntry child) {
		if (childrenList == null) {
			childrenList = new ArrayList<MagicEntry>();
		}
		childrenList.add(child);
	}

	/**
	 * Called after parsing of an entry has completed. This allows us to tighten up some memory.
	 */
	public void parseComplete() {
		if (childrenList != null) {
			childrenArray = childrenList.toArray(new MagicEntry[childrenList.size()]);
			// help gc
			childrenList.clear();
			childrenList = null;
			// recurse to complete the children, and the children's children, ...
			for (MagicEntry entry : childrenArray) {
				entry.parseComplete();
			}
		}
	}

	void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("level ").append(level);
		if (name != null) {
			sb.append(",name '").append(name).append('\'');
		}
		if (mimeType != null) {
			sb.append(",mime '").append(mimeType).append('\'');
		}
		if (testValue != null) {
			sb.append(",test '").append(testValue).append('\'');
		}
		if (formatter != null) {
			sb.append(",format '").append(formatter).append('\'');
		}
		return sb.toString();
	}

	/**
	 * Main processing method which can go recursive.
	 */
	private ContentData processBytes(byte[] bytes, int prevOffset, ContentData contentData) {
		int offset = this.offset;
		if (offsetInfo != null) {
			offset = offsetInfo.getOffset(bytes);
		}
		if (addOffset) {
			offset = prevOffset + offset;
		}
		Object val = matcher.extractValueFromBytes(offset, bytes);
		if (val == null) {
			return null;
		}
		if (testValue != null) {
			val = matcher.isMatch(testValue, andValue, unsignedType, val, offset, bytes);
			if (val == null) {
				return null;
			}
		}

		if (contentData == null) {
			contentData = new ContentData(name, mimeType);
			// default is a child didn't match, set a partial so the matcher will keep looking
			contentData.partial = true;
		}
		if (formatter != null) {
			// if we are appending and need a space then prepend one
			if (formatSpacePrefix && contentData.sb.length() > 0) {
				contentData.sb.append(' ');
			}
			matcher.renderValue(contentData.sb, val, formatter);
		}

		if (childrenArray == null) {
			// no children so we have a full match and can set partial to false
			contentData.partial = false;
		} else {
			for (MagicEntry child : childrenArray) {
				child.processBytes(bytes, offset, contentData);
				// NOTE: we continue to match to see if we can add additional information to the name
			}
		}
		/*
		 * Now that we have processed this entry (either with or without children), see if we still need to annotate the
		 * content information.
		 * 
		 * NOTE: the children will have the first opportunity to set this which makes sense since they are the most
		 * specific.
		 */
		if (name != UNKNOWN_NAME && contentData.name == UNKNOWN_NAME) {
			contentData.setName(name);
		}
		if (mimeType != null && contentData.mimeType == null) {
			contentData.mimeType = mimeType;
		}
		return contentData;
	}

	/**
	 * Internal processing data about the content.
	 */
	private static class ContentData {
		String name;
		boolean partial;
		String mimeType;
		final StringBuilder sb = new StringBuilder();
		private ContentData(String name, String mimeType) {
			this.name = name;
			this.mimeType = mimeType;
		}
		public void setName(String name) {
			this.name = name;
		}
		@Override
		public String toString() {
			if (sb.length() == 0) {
				if (name == null) {
					return super.toString();
				} else {
					return name;
				}
			} else {
				return sb.toString();
			}
		}
	}

	/**
	 * Information about the extended offset.
	 */
	static class OffsetInfo {

		final int offset;
		final EndianConverter converter;
		final boolean isId3;
		final int size;
		final int add;

		OffsetInfo(int offset, EndianConverter converter, boolean isId3, int size, int add) {
			this.offset = offset;
			this.converter = converter;
			this.isId3 = isId3;
			this.size = size;
			this.add = add;
		}

		public Integer getOffset(byte[] bytes) {
			Long val;
			if (isId3) {
				val = (Long) converter.convertId3(offset, bytes, size);
			} else {
				val = (Long) converter.convertNumber(offset, bytes, size);
			}
			if (val == null) {
				return null;
			} else {
				return (int) (val + add);
			}
		}
	}
}
