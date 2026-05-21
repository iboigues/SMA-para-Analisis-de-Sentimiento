package youtube;

import com.google.api.services.youtube.model.CommentThread;

import java.util.List;

public record YoutubeResponse(String title, List<CommentThread> comments) {}
