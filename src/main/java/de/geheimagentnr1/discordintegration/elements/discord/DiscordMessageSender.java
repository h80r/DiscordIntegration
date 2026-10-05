package de.geheimagentnr1.discordintegration.elements.discord;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


@Log4j2
public class DiscordMessageSender {
	
	
	private static final int MAX_DISCORD_MESSAGE_LENGTH = 2000;
	
	@NotNull
	private final HttpClient httpClient;
	
	public DiscordMessageSender() {
		
		httpClient = HttpClient.newBuilder()
			.connectTimeout( Duration.ofSeconds( 10 ) )
			.build();
	}
	
	public void sendMessage( @NotNull GuildMessageChannel channel, @NotNull String message ) {
		
		try {
			for( int start = 0; start < message.length(); start += MAX_DISCORD_MESSAGE_LENGTH ) {
				channel.sendMessage( message.substring( start, Math.min( message.length(), start + MAX_DISCORD_MESSAGE_LENGTH ) ) ).queue();
			}
		} catch( Exception exception ) {
			log.error( "Message could not be send to channel {}", channel.getIdLong(), exception );
		}
	}
	
	public void sendWebhookMessage(
		@NotNull String webhookUrl,
		@NotNull String message,
		@Nullable String username,
		@Nullable String avatarUrl,
		long threadId ) {
		
		try {
			String targetUrl = threadId > 0 ? webhookUrl + "?thread_id=" + threadId : webhookUrl;
			for( int start = 0; start < message.length(); start += MAX_DISCORD_MESSAGE_LENGTH ) {
				String part = message.substring( start, Math.min( message.length(), start + MAX_DISCORD_MESSAGE_LENGTH ) );
				sendWebhookMessagePart( targetUrl, part, username, avatarUrl );
			}
		} catch( Exception exception ) {
			log.error( "Webhook message could not be send to {}", webhookUrl, exception );
		}
	}
	
	private void sendWebhookMessagePart(
		@NotNull String webhookUrl,
		@NotNull String message,
		@Nullable String username,
		@Nullable String avatarUrl ) {
		
		StringBuilder json = new StringBuilder();
		json.append( "{\"content\":\"" ).append( escapeJson( message ) ).append( "\"" );
		if( username != null && !username.isBlank() ) {
			json.append( ",\"username\":\"" )
				.append( escapeJson( username.substring( 0, Math.min( username.length(), 80 ) ) ) )
				.append( "\"" );
		}
		if( avatarUrl != null && !avatarUrl.isBlank() ) {
			json.append( ",\"avatar_url\":\"" ).append( escapeJson( avatarUrl ) ).append( "\"" );
		}
		json.append( "}" );
		
		HttpRequest request = HttpRequest.newBuilder()
			.uri( URI.create( webhookUrl ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();
		
		httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
			.thenAccept( response -> {
				if( response.statusCode() < 200 || response.statusCode() >= 300 ) {
					log.error(
						"Webhook returned status {}: {}",
						response.statusCode(),
						response.body()
					);
				}
			} )
			.exceptionally( throwable -> {
				log.error( "Webhook request failed: {}", webhookUrl, throwable );
				return null;
			} );
	}
	
	@NotNull
	private String escapeJson( @NotNull String value ) {
		
		return value.replace( "\\", "\\\\" )
			.replace( "\"", "\\\"" )
			.replace( "\n", "\\n" )
			.replace( "\r", "\\r" )
			.replace( "\t", "\\t" );
	}
}
