package com.example.quicktime;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuickTime implements ModInitializer {

	private static final int DAY = 1000, NOON = 6000, SUNSET = 12000,
			NIGHT = 13000, MIDNIGHT = 18000, SUNRISE = 23000;
	private static final int DAY_LENGTH = 24000;

	private static final Pattern CLOCK =
			Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|h)?$", Pattern.CASE_INSENSITIVE);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			shortcut(dispatcher, "day", DAY);
			shortcut(dispatcher, "night", NIGHT);
			shortcut(dispatcher, "noon", NOON);
			shortcut(dispatcher, "midnight", MIDNIGHT);
			shortcut(dispatcher, "sunrise", SUNRISE);
			shortcut(dispatcher, "sunset", SUNSET);

			dispatcher.register(Commands.literal("time")
					.then(Commands.literal("set")
							.then(Commands.argument("clock", StringArgumentType.greedyString())
									.executes(ctx -> {
										String raw = StringArgumentType.getString(ctx, "clock").trim();
										Integer ticks = parseClock(raw);
										if (ticks == null) {
											ctx.getSource().sendFailure(Component.literal(
													"Unknown time '" + raw + "'. Try 6am, 6:30pm, 18:00, day, noon, sunset..."));
											return 0;
										}
										return setTimeOfDay(ctx.getSource(), ticks);
									}))));
		});
	}

	private static void shortcut(CommandDispatcher<CommandSourceStack> d, String name, int ticks) {
		d.register(Commands.literal(name).executes(ctx -> setTimeOfDay(ctx.getSource(), ticks)));
	}

	private static int setTimeOfDay(CommandSourceStack source, int target) {
		CommandDispatcher<CommandSourceStack> d = source.getServer().getCommands().getDispatcher();
		try {
			int now = d.execute("time query minecraft:day", source.withSuppressedOutput());
			int delta = Math.floorMod(target - now, DAY_LENGTH);
			return d.execute("time add " + delta, source);
		} catch (CommandSyntaxException e) {
			source.sendFailure(Component.literal("Couldn't change the time: " + e.getMessage()));
			return 0;
		}
	}

	private static Integer parseClock(String raw) {
		Matcher m = CLOCK.matcher(raw);
		if (!m.matches()) return null;
		int hour = Integer.parseInt(m.group(1));
		int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
		String suffix = m.group(3) == null ? "" : m.group(3).toLowerCase();
		if (min > 59) return null;
		if (suffix.equals("am") || suffix.equals("pm")) {
			if (hour < 1 || hour > 12) return null;
			hour = hour % 12 + (suffix.equals("pm") ? 12 : 0);
		} else if (hour > 23) {
			return null;
		}
		double ticks = (hour - 6) * 1000.0 + min * (1000.0 / 60.0);
		return (int) (((Math.round(ticks) % DAY_LENGTH) + DAY_LENGTH) % DAY_LENGTH);
	}
}
