package com.njhyuk.hotplace.service

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.support.ui.ExpectedConditions
import java.time.Duration
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.net.URL
import org.openqa.selenium.WebElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Service
class WebScraperService {
    private val logger = LoggerFactory.getLogger(WebScraperService::class.java)
    private var driver: WebDriver? = null
    private var js: JavascriptExecutor? = null
    private var wait: WebDriverWait? = null

    companion object {
        private const val TIMEOUT_SECONDS = 20L
        private const val TARGET_ELEMENT_SELECTOR = "[data-pui-click-code=rvshowmore]"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val MORE_BUTTON_SELECTOR = ".NSTUp"
        private const val MAX_CLICKS = 5
        private const val REVIEW_URL_PATTERN = "https://pcmap.place.naver.com/restaurant/\\d+/review/visitor"
        private const val MAP_URL_PATTERN = "https://map.naver.com/p/entry/place/\\d+"
        private const val REDIRECTED_REVIEW_URL_PATTERN = "https://map.naver.com/p/entry/place/\\d+\\?placePath=.*"
    }

    private fun initDriver() {
        if (driver == null) {
            System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver")
            driver = ChromeDriver(createChromeOptions())
            js = driver as JavascriptExecutor
            wait = WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SECONDS))
        }
    }

    private fun createChromeOptions(): ChromeOptions {
        val options = ChromeOptions()
        options.addArguments(
            "--headless=new",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--remote-debugging-port=9222",
            "--disable-extensions",
            "--disable-software-rasterizer",
            "--disable-setuid-sandbox",
            "--disable-infobars",
            "--disable-notifications",
            "--disable-popup-blocking",
            "--disable-web-security",
            "--allow-running-insecure-content",
            "--window-size=1920,1080"
        )
        options.setBinary("/usr/bin/chromium")
        return options
    }

    private fun convertToReviewUrl(mapUrl: String): String {
        val placeId = mapUrl.split("/").last().split("?").first()
        return "https://pcmap.place.naver.com/restaurant/$placeId/review/visitor"
    }

    private fun isReviewPage(url: String): Boolean {
        return url.matches(Regex(REVIEW_URL_PATTERN)) || 
               url.matches(Regex(REDIRECTED_REVIEW_URL_PATTERN))
    }

    private fun extractPlaceName(): String {
        val metaTitle = driver?.findElement(By.cssSelector("meta[property='og:title']"))?.getAttribute("content")
        return metaTitle?.split(" : ")?.first() ?: ""
    }

    suspend fun scrapeWebPage(url: String): String = withContext(Dispatchers.IO) {
        initDriver()
        return@withContext try {
            logger.info("Starting to load URL: $url")
            
            // Convert map.naver.com URL to pcmap.place.naver.com URL if needed
            val targetUrl = if (url.matches(Regex(MAP_URL_PATTERN))) {
                val reviewUrl = convertToReviewUrl(url)
                logger.info("Converting URL from $url to $reviewUrl")
                reviewUrl
            } else {
                url
            }
            
            driver?.get(targetUrl)
            
            // Wait for redirect and get the final URL
            waitForPageLoad()
            var finalUrl = driver?.currentUrl ?: ""
            logger.info("Redirected to: $finalUrl")

            // If we got redirected to map.naver.com, convert and navigate to the actual review page
            if (finalUrl.matches(Regex(REDIRECTED_REVIEW_URL_PATTERN))) {
                val actualReviewUrl = convertToReviewUrl(finalUrl)
                logger.info("Converting redirected URL to actual review page: $actualReviewUrl")
                driver?.get(actualReviewUrl)
                waitForPageLoad()
                finalUrl = driver?.currentUrl ?: ""
                logger.info("Now on actual review page: $finalUrl")
            }

            // Check if we're on the correct review page
            if (!finalUrl.matches(Regex(REVIEW_URL_PATTERN))) {
                logger.error("Not on the expected review page. Current URL: $finalUrl")
                throw RuntimeException("Failed to navigate to the review page")
            }
            
            // Extract place name from og:title
            val placeName = extractPlaceName()
            logger.info("Extracted place name: $placeName")
            
            waitForReactElements()
            clickMoreButtonMultipleTimes()
            
            val elements = findTargetElements()
            if (elements.isEmpty()) {
                logger.warn("No matching elements found")
                return@withContext "No matching elements found"
            }

            val reviews = extractAndFormatTexts(elements)
            "storeName:$placeName\n$reviews"
        } catch (e: Exception) {
            logger.error("Error occurred while scraping webpage: ${e.message}", e)
            throw RuntimeException("Failed to scrape webpage: ${e.message}", e)
        }
    }

    private suspend fun waitForPageLoad() = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            wait?.until {
                val readyState = js?.executeScript("return document.readyState") as String
                logger.debug("Current page readyState: $readyState")
                readyState == "complete"
            }
            logger.info("Page load completed")
            continuation.resume(Unit)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    private suspend fun waitForReactElements() = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            logger.info("Waiting for React elements to render...")
            wait?.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TARGET_ELEMENT_SELECTOR)))
            continuation.resume(Unit)
        } catch (e: Exception) {
            logger.info("No more button found or all reviews are already loaded")
            val elements = driver?.findElements(By.cssSelector(TARGET_ELEMENT_SELECTOR)) ?: emptyList<WebElement>()
            if (elements.isEmpty()) {
                logger.warn("No reviews found on the page")
            } else {
                logger.info("Found "+elements.size+" reviews without more button")
            }
            continuation.resume(Unit)
        }
    }

    private fun findTargetElements(): List<WebElement> {
        logger.info("Finding elements using Selenium API...")
        val elements = driver?.findElements(By.cssSelector(TARGET_ELEMENT_SELECTOR)) ?: emptyList<WebElement>()
        logger.info("Found "+elements.size+" elements")
        return elements
    }

    private fun extractAndFormatTexts(elements: List<WebElement>): String {
        val texts = elements.mapNotNull { element ->
            try {
                logger.debug("Element HTML: ${element.getAttribute("outerHTML")}")
                val text = element.text.trim()
                logger.debug("Element text: $text")
                if (text.isNotEmpty()) "- $text" else null
            } catch (e: Exception) {
                logger.error("Error getting element text: ${e.message}")
                null
            }
        }

        logger.info("Collected ${texts.size} non-empty texts")
        
        return if (texts.isEmpty()) {
            "No text content found in matching elements"
        } else {
            texts.joinToString("\n")
        }
    }

    private suspend fun clickMoreButtonMultipleTimes() = withContext(Dispatchers.IO) {
        logger.info("Attempting to click '더보기' button multiple times...")
        var clickCount = 0
        var previousElementCount = 0
        var consecutiveNoNewElements = 0
        val initialElements = driver?.findElements(By.cssSelector(TARGET_ELEMENT_SELECTOR)) ?: emptyList()
        if (initialElements.isEmpty()) {
            logger.info("No reviews found on the page")
            return@withContext
        }
        while (clickCount < MAX_CLICKS) {
            try {
                val moreButton = WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(MORE_BUTTON_SELECTOR)))
                if (!moreButton.isDisplayed || !moreButton.isEnabled) {
                    logger.info("More button is no longer visible or enabled after $clickCount clicks")
                    break
                }
                previousElementCount = driver?.findElements(By.cssSelector(TARGET_ELEMENT_SELECTOR))?.size ?: 0
                moreButton.click()
                clickCount++
                logger.info("Clicked '더보기' button $clickCount times")
                try {
                    wait?.until {
                        val currentElementCount = driver?.findElements(By.cssSelector(TARGET_ELEMENT_SELECTOR))?.size ?: 0
                        currentElementCount > previousElementCount
                    }
                    consecutiveNoNewElements = 0
                    logger.info("New elements loaded: "+((driver?.findElements(By.cssSelector(TARGET_ELEMENT_SELECTOR))?.size ?: 0) - previousElementCount))
                } catch (e: Exception) {
                    consecutiveNoNewElements++
                    logger.warn("No new elements loaded after click $clickCount. Consecutive failures: $consecutiveNoNewElements")
                    if (consecutiveNoNewElements >= 2) {
                        logger.info("Stopping after $consecutiveNoNewElements consecutive failures to load new elements")
                        break
                    }
                }
            } catch (e: Exception) {
                logger.info("No more '더보기' button found or all reviews are already loaded after $clickCount clicks")
                break
            }
        }
        val finalElementCount = driver?.findElements(By.cssSelector(TARGET_ELEMENT_SELECTOR))?.size ?: 0
        logger.info("Finished processing reviews. Total clicks: $clickCount, Total elements found: $finalElementCount")
    }

    fun close() {
        driver?.quit()
        driver = null
        js = null
        wait = null
    }
} 
