package com.ewolff.microservice.invoicing.poller;

import java.util.Date;

import org.apache.http.client.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ewolff.microservice.invoicing.Invoice;
import com.ewolff.microservice.invoicing.InvoiceRepository;
import com.ewolff.microservice.invoicing.InvoiceService;
import com.rometools.rome.feed.atom.Entry;
import com.rometools.rome.feed.atom.Feed;

@Component
public class InvoicePoller {

	private final Logger log = LoggerFactory.getLogger(InvoicePoller.class);

	private String url = "";

	private RestTemplate restTemplate = new RestTemplate();

	private Date lastModified = null;

	private InvoiceRepository invoiceRepository;

	private boolean pollingActivated;

	private InvoiceService invoiceService;

	@Autowired
	public InvoicePoller(@Value("${order.url}") String url, @Value("${poller.actived:true}") boolean pollingActivated,
			InvoiceRepository invoiceRepository, InvoiceService invoiceService) {
		super();
		this.pollingActivated = pollingActivated;
		this.url = url;
		this.invoiceRepository = invoiceRepository;
		this.invoiceService = invoiceService;
	}

	@Scheduled(fixedDelay = 30000)
	public void poll() {
		if (pollingActivated) {
			pollInternal();
		}
	}

	public void pollInternal() {
		HttpHeaders requestHeaders = new HttpHeaders();
		if (lastModified != null) {
			requestHeaders.set("If-Modified-Since", DateUtils.formatDate(lastModified));
		}
		HttpEntity<?> requestEntity = new HttpEntity(requestHeaders);
		ResponseEntity<Feed> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Feed.class);

		if (response.getStatusCode() != HttpStatus.NOT_MODIFIED) {
			log.trace("data has been modified");
			Feed feed = response.getBody();
			for (Entry entry : feed.getEntries()) {
				if ((lastModified == null) || (entry.getUpdated().after(lastModified))) {
					Invoice invoice = restTemplate
							.getForEntity(entry.getContents().get(0).getSrc(), Invoice.class).getBody();
					log.trace("saving invoice {}", invoice.getId());
					invoiceService.generateInvoice(invoice);
				}
			}
			if (response.getHeaders().getFirst("Last-Modified") != null) {
				lastModified = DateUtils.parseDate(response.getHeaders().getFirst("Last-Modified"));
				log.trace("Last-Modified header {}", lastModified);
			}
		} else {
			log.trace("no new data");
		}
	}

}
