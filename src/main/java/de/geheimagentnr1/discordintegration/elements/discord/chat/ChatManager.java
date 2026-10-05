package de.geheimagentnr1.discordintegration.elements.discord.chat;

import de.geheimagentnr1.discordintegration.DiscordIntegration;
import de.geheimagentnr1.discordintegration.elements.discord.AbstractDiscordIntegrationServiceProvider;
import de.geheimagentnr1.discordintegration.elements.discord.DiscordManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@SuppressWarnings( { "SynchronizeOnThis", "NestedSynchronizedStatement" } )
@Log4j2
@RequiredArgsConstructor
public class ChatManager extends AbstractDiscordIntegrationServiceProvider {
	
	
	@Getter( AccessLevel.PROTECTED )
	@NotNull
	private final DiscordIntegration discordIntegration;
	
	@Nullable
	private GuildMessageChannel messageChannel;
	
	public void init() {
		
		synchronized( DiscordManager.class ) {
			synchronized( ChatManager.class ) {
				stop();
				if( shouldInitialize() ) {
					long channelId = serverConfig().getChatConfig().getChannelId();
					long threadId = serverConfig().getChatConfig().getThreadId();
					JDA jda = discordManager().getJda();
					TextChannel textChannel = jda.getTextChannelById( channelId );
					if( textChannel == null ) {
						log.error( "Discord Chat Text Channel {} not found", channelId );
						return;
					}
					if( threadId > 0 ) {
						ThreadChannel threadChannel = jda.getThreadChannelById( threadId );
						if( threadChannel == null ) {
							log.error( "Discord Chat Thread {} not found", threadId );
							return;
						}
						messageChannel = threadChannel;
					} else {
						messageChannel = textChannel;
					}
				}
			}
		}
	}
	
	public void stop() {
		
		synchronized( DiscordManager.class ) {
			synchronized( ChatManager.class ) {
				messageChannel = null;
			}
		}
	}
	
	private boolean shouldInitialize() {
		
		return discordManager().isInitialized() && serverConfig().getChatConfig().isEnabled();
	}
	
	private boolean isInitialized() {
		
		synchronized( DiscordManager.class ) {
			synchronized( ChatManager.class ) {
				return shouldInitialize() && messageChannel != null;
			}
		}
	}
	
	//package-private
	boolean isCorrectChannel( long channelId ) {
		
		if( !isInitialized() ) {
			return false;
		}
		long configuredChannelId = serverConfig().getChatConfig().getChannelId();
		long configuredThreadId = serverConfig().getChatConfig().getThreadId();
		if( configuredThreadId > 0 ) {
			return configuredThreadId == channelId;
		}
		return configuredChannelId == channelId;
	}
	
	public void sendEmoteChatMessage( CommandSourceStack source, Component action ) {
		
		ServerPlayer player = getPlayer( source );
		if( player != null && useWebhook() ) {
			sendWebhookMessage(
				player,
				discordMessageBuilder().buildWebhookMeMessage( source, action.getString() )
			);
		} else {
			sendMessage( discordMessageBuilder().buildMeChatMessage( source, action.getString() ) );
		}
	}
	
	public void sendChatMessage( CommandSourceStack source, Component message ) {
		
		if( !serverConfig().getChatConfig().suppressServerMessages() ||
			!"Server".equals( source.getTextName() ) ||
			source.getEntity() != null ) {
			ServerPlayer player = getPlayer( source );
			if( player != null && useWebhook() ) {
				sendWebhookMessage(
					player,
					discordMessageBuilder().buildWebhookChatMessage( source, message.getString() )
				);
			} else {
				sendMessage( discordMessageBuilder().buildChatMessage( source, message ) );
			}
		}
	}
	
	public void sendChatMessage( ServerPlayer player, String message ) {
		
		if( useWebhook() ) {
			sendWebhookMessage(
				player,
				discordMessageBuilder().buildWebhookChatMessage( player, message )
			);
		} else {
			sendMessage( discordMessageBuilder().buildChatMessage( player, message ) );
		}
	}
	
	@Nullable
	private ServerPlayer getPlayer( @NotNull CommandSourceStack source ) {
		
		if( source.getEntity() instanceof ServerPlayer player ) {
			return player;
		}
		return null;
	}
	
	private boolean useWebhook() {
		
		return serverConfig().getChatConfig().useWebhook() &&
			!serverConfig().getChatConfig().getWebhookUrl().isBlank();
	}
	
	public void sendMessage( String message ) {
		
		synchronized( DiscordManager.class ) {
			synchronized( ChatManager.class ) {
				if( isInitialized() ) {
					discordMessageSender().sendMessage( messageChannel, message );
				}
			}
		}
	}
	
	private void sendWebhookMessage( @NotNull ServerPlayer player, @NotNull String message ) {
		
		synchronized( DiscordManager.class ) {
			synchronized( ChatManager.class ) {
				if( shouldInitialize() ) {
					String playerName = player.getGameProfile().getName();
					String username = discordMessageBuilder().buildWebhookUsername( playerName );
					String avatarUrl = discordMessageBuilder().buildWebhookAvatarUrl( player.getUUID(), playerName );
					discordMessageSender().sendWebhookMessage(
						serverConfig().getChatConfig().getWebhookUrl(),
						message,
						username,
						avatarUrl,
						serverConfig().getChatConfig().getThreadId()
					);
				}
			}
		}
	}
	
	//package-private
	void sendFeedbackMessage( String message ) {
		
		synchronized( DiscordManager.class ) {
			synchronized( ChatManager.class ) {
				if( isInitialized() ) {
					for( String messagePart : discordMessageBuilder().buildFeedbackMessage( message ) ) {
						discordMessageSender().sendMessage( messageChannel, messagePart );
					}
				}
			}
		}
	}
}
