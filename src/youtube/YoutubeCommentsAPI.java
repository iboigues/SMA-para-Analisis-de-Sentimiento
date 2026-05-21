package youtube;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadListResponse;
import com.google.api.services.youtube.model.VideoListResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YoutubeCommentsAPI {
  private static final String APPLICATION_NAME = "APP";
  private static final String API_KEY = "AIzaSyDnFtOlLiRubZb6E6c1f5w0cAB-iRK29UM";

  public static YoutubeResponse getComments(String videoID, int maxComments) {
    if(maxComments == -1)
      maxComments = Integer.MAX_VALUE;

    try {
      YouTube youTubeService = new YouTube.Builder(
          GoogleNetHttpTransport.newTrustedTransport(),
          GsonFactory.getDefaultInstance(),
          null
      ).setApplicationName(APPLICATION_NAME)
          .build();

      YouTube.Videos.List requestVideo = youTubeService.videos().list(List.of("snippet"));
      YouTube.CommentThreads.List requestComments = youTubeService.commentThreads().list(List.of("snippet"));

      requestVideo.setKey(API_KEY)
          .setId(new ArrayList<>(Collections.singleton(videoID)))
          .setMaxResults(1L);

      VideoListResponse videoResponse = requestVideo.execute();

      String title = "";

      if (!videoResponse.getItems().isEmpty()) {
        title = videoResponse.getItems()
            .getFirst()
            .getSnippet()
            .getTitle();
      }

      requestComments.setKey(API_KEY)
          .setVideoId(videoID)
          .setMaxResults(maxComments > 100L ? 100L : maxComments)
          .setTextFormat("plainText");

      List<CommentThread> comments = new ArrayList<>();

      String nextPageToken = null;
      int total = 0;

      do {
        requestComments.setPageToken(nextPageToken);

        CommentThreadListResponse response = requestComments.execute();

        for (CommentThread commentThread : response.getItems()){
          comments.add(commentThread);
          total++;

          if(total >= maxComments)
            return new YoutubeResponse(title, comments);
        }

        nextPageToken = response.getNextPageToken();

      } while (nextPageToken != null);

      return new YoutubeResponse(title, comments);

    } catch (GeneralSecurityException | IOException e) {
      return new YoutubeResponse("", new ArrayList<>());
    }
  }
}
