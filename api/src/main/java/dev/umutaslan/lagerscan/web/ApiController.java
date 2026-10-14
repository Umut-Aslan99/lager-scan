package dev.umutaslan.lagerscan.web;

import dev.umutaslan.lagerscan.booking.BookingService;
import dev.umutaslan.lagerscan.catalog.Catalog;
import dev.umutaslan.lagerscan.domain.DomainException;
import dev.umutaslan.lagerscan.domain.Photo;
import dev.umutaslan.lagerscan.domain.Repositories.ArticleRepo;
import dev.umutaslan.lagerscan.domain.Repositories.BookingRepo;
import dev.umutaslan.lagerscan.domain.Repositories.PhotoRepo;
import dev.umutaslan.lagerscan.domain.Repositories.WorkspaceRepo;
import dev.umutaslan.lagerscan.events.LiveEvents;
import dev.umutaslan.lagerscan.web.Dtos.*;
import dev.umutaslan.lagerscan.workspace.Clock;
import dev.umutaslan.lagerscan.workspace.WorkspaceFilter;
import dev.umutaslan.lagerscan.workspace.WorkspaceService;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Catalog catalog;
    private final BookingService bookingService;
    private final BookingRepo bookings;
    private final ArticleRepo articles;
    private final PhotoRepo photos;
    private final WorkspaceRepo workspaceRepo;
    private final WorkspaceService workspaces;
    private final LiveEvents events;
    private final Clock clock;
    private final String publicUrl;

    public ApiController(Catalog catalog, BookingService bookingService, BookingRepo bookings, ArticleRepo articles, PhotoRepo photos,
                         WorkspaceRepo workspaceRepo, WorkspaceService workspaces, LiveEvents events, Clock clock,
                         @Value("${lagerscan.public-url}") String publicUrl) {
        this.catalog = catalog;
        this.bookingService = bookingService;
        this.bookings = bookings;
        this.articles = articles;
        this.photos = photos;
        this.workspaceRepo = workspaceRepo;
        this.workspaces = workspaces;
        this.events = events;
        this.clock = clock;
        this.publicUrl = publicUrl;
    }

    @GetMapping("/health")
    public String health() { return "ok"; }

    @GetMapping("/workspace")
    public WorkspaceView workspace(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws) {
        var w = workspaceRepo.findById(ws).orElseThrow();
        return new WorkspaceView(publicUrl + "/join/" + w.getJoinToken(), w.getCreatedAt(), WorkspaceService.IDLE_LIFETIME.toHours());
    }

    @PostMapping("/workspace/reset")
    public ResponseEntity<Void> reset(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws) {
        workspaces.reset(ws);
        events.publish(ws, "reset", Map.of("at", clock.now().toString()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scan")
    public ResponseEntity<ScanResult> scan(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws, @RequestParam String code) {
        return catalog.resolve(ws, code).map(ResponseEntity::ok)
            .orElseThrow(() -> new DomainException("Code „" + code.trim() + "“ ist weder Artikel noch Lagerplatz."));
    }

    @GetMapping("/articles")
    public List<ArticleSummary> articles(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws, @RequestParam(required = false) String q) {
        return catalog.articleList(ws, q);
    }

    @GetMapping("/articles/{number}")
    public ResponseEntity<ArticleView> article(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws, @PathVariable String number) {
        return ResponseEntity.of(catalog.article(ws, number));
    }

    @GetMapping("/bins")
    public List<BinView> bins(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws) {
        return catalog.binList(ws);
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws) {
        return catalog.dashboard(ws, clock.startOfToday(), events.listenerCount(ws));
    }

    @GetMapping("/labels")
    public Labels labels(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws) {
        return catalog.labels(ws);
    }

    @PostMapping(value = "/bookings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingService.Result> book(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws, @RequestBody BookingService.Request request) {
        try {
            var result = bookingService.book(ws, request);
            return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
        } catch (DataIntegrityViolationException race) {
            // The same offline booking arrived twice at the same moment: the first one won.
            return ResponseEntity.ok(bookingService.book(ws, request));
        }
    }

    @GetMapping("/bookings")
    public List<BookingView> recent(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws, @RequestParam(defaultValue = "50") int limit) {
        return catalog.dashboard(ws, clock.startOfToday(), 0).recent().stream().limit(Math.min(limit, 100)).toList();
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws,
                                                      @RequestParam MultipartFile file, @RequestParam(required = false) String articleNumber) throws IOException {
        if (file.isEmpty() || file.getSize() > 3 * 1024 * 1024) throw new DomainException("Das Foto ist leer oder größer als 3 MB.");
        if (!IMAGE_TYPES.contains(file.getContentType())) throw new DomainException("Nur JPEG-, PNG- oder WebP-Bilder.");
        var article = articleNumber == null ? null : articles.findByWorkspaceIdAndNumberIgnoreCase(ws, articleNumber).orElseThrow(() -> new DomainException("Artikel unbekannt."));
        var photo = photos.save(new Photo(ws, article == null ? null : article.getNumber(), file.getContentType(), file.getBytes(), clock.now()));
        if (article != null && article.getPhotoId() == null) {
            article.setPhotoId(photo.getId());
            articles.save(article);
        }
        return ResponseEntity.created(URI.create("/api/photos/" + photo.getId())).body(Map.of("id", photo.getId(), "url", "/api/photos/" + photo.getId()));
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws, @PathVariable UUID id) {
        return photos.findByIdAndWorkspaceId(id, ws)
            .map(p -> ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate()).body(p.getData()))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestAttribute(WorkspaceFilter.ATTRIBUTE) UUID ws) {
        return events.subscribe(ws);
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail domain(DomainException e) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        p.setTitle("Buchung nicht möglich");
        return p;
    }
}
