package me.confuser.barapi;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * This is a utility class for BarAPI.
 * It is based on the code by SoThatsIt.
 * 
 * http://forums.bukkit.org/threads/tutorial-utilizing-the-boss-health-bar.158018/page-2#post-1760928
 * 
 * @author James Mortemore
 */
public class Util {

	public static Integer ENTITY_ID = 6000;

	// Reflection Util
	public static void sendPacket(Player p, Object packet) {
		try {
			Object nmsPlayer = getHandle(p);
			Field con_field = nmsPlayer.getClass().getField("playerConnection");
			Object con = con_field.get(nmsPlayer);
			Method packet_method = getMethod(con.getClass(), "sendPacket");
			packet_method.invoke(con, packet);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
	}

	public static Class<?> getCraftClass(String ClassName) {
		String name = Bukkit.getServer().getClass().getPackage().getName();
		String version = name.substring(name.lastIndexOf('.') + 1) + ".";
		String className = "net.minecraft.server." + version + ClassName;
		Class<?> c = null;
		try {
			c = Class.forName(className);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return c;
	}

	public static Object getHandle(Entity entity) {
		Object nms_entity = null;
		Method entity_getHandle = getMethod(entity.getClass(), "getHandle");
		try {
			nms_entity = entity_getHandle.invoke(entity);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return nms_entity;
	}

	public static Field getField(Class<?> cl, String field_name) {
		try {
			Field field = cl.getDeclaredField(field_name);
			return field;
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Method getMethod(Class<?> cl, String method, Class<?>[] args) {
		for (Method m : cl.getMethods()) {
			if (m.getName().equals(method) && ClassListEqual(args, m.getParameterTypes())) {
				return m;
			}
		}
		return null;
	}

	public static Method getMethod(Class<?> cl, String method, Integer args) {
		for (Method m : cl.getMethods()) {
			if (m.getName().equals(method) && args.equals(new Integer(m.getParameterTypes().length))) {
				return m;
			}
		}
		return null;
	}

	public static Method getMethod(Class<?> cl, String method) {
		for (Method m : cl.getMethods()) {
			if (m.getName().equals(method)) {
				return m;
			}
		}
		return null;
	}

	public static boolean ClassListEqual(Class<?>[] l1, Class<?>[] l2) {
		boolean equal = true;

		if (l1.length != l2.length)
			return false;
		for (int i = 0; i < l1.length; i++) {
			if (l1[i] != l2[i]) {
				equal = false;
				break;
			}
		}

		return equal;
	}

}