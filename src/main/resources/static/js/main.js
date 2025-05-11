document.addEventListener('DOMContentLoaded', () => {
    const analyzeBtn = document.getElementById('analyzeBtn');
    const reviewUrl = document.getElementById('reviewUrl');
    const resultContainer = document.querySelector('.result-container');
    const summaryText = document.getElementById('summaryText');
    const positivesList = document.getElementById('positivesList');
    const negativesList = document.getElementById('negativesList');
    const loadingIndicator = document.createElement('div');
    loadingIndicator.className = 'loading-indicator';
    loadingIndicator.innerHTML = `
        <div class="spinner"></div>
        <p>리뷰를 분석하고 있습니다...</p>
    `;
    document.querySelector('.review-input').appendChild(loadingIndicator);
    loadingIndicator.style.display = 'none';

    analyzeBtn.addEventListener('click', async () => {
        const url = reviewUrl.value.trim();
        
        if (!url) {
            alert('네이버 플레이스 리뷰탭 URL을 입력해주세요.');
            return;
        }

        if (!url.includes('place.naver.com') && !url.includes('naver.me')) {
            alert('올바른 네이버 플레이스 URL을 입력해주세요.');
            return;
        }

        try {
            // 기존 결과 숨기기
            resultContainer.style.display = 'none';
            
            // 로딩 상태 표시
            analyzeBtn.disabled = true;
            analyzeBtn.textContent = '분석 중...';
            loadingIndicator.style.display = 'flex';

            const response = await fetch('/api/reviews/analyze', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ url })
            });

            if (!response.ok) {
                throw new Error('분석 중 오류가 발생했습니다.');
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let result = null;
            let error = null;

            while (true) {
                const { value, done } = await reader.read();
                if (done) break;
                
                const chunk = decoder.decode(value);
                const lines = chunk.split('\n');
                let currentEvent = null;
                let currentData = null;
                
                for (const line of lines) {
                    if (line.startsWith('event:')) {
                        currentEvent = line.slice(6).trim();
                    } else if (line.startsWith('data:')) {
                        currentData = line.slice(5).trim();
                    }
                    
                    if (currentEvent && currentData) {
                        try {
                            const parsed = JSON.parse(currentData);
                            if (currentEvent === 'error') {
                                error = parsed.message;
                            } else {
                                result = parsed;
                            }
                            currentEvent = null;
                            currentData = null;
                        } catch (e) {
                            console.error('Failed to parse SSE data:', e);
                        }
                    }
                }
            }

            if (error) {
                throw new Error(error);
            }

            if (!result) {
                throw new Error('분석 결과를 받지 못했습니다.');
            }
            
            // 상점명 표시
            document.getElementById('storeName').textContent = result.storeName;
            
            // 결과 표시
            summaryText.textContent = result.summary;
            
            // 긍정적 의견 표시
            positivesList.innerHTML = result.positives
                .map(positive => `<li>${positive}</li>`)
                .join('');
            
            // 부정적 의견 표시
            negativesList.innerHTML = result.negatives
                .map(negative => `<li>${negative}</li>`)
                .join('');

            resultContainer.style.display = 'block';

            // 결과 컨테이너로 스크롤
            resultContainer.scrollIntoView({ behavior: 'smooth' });

        } catch (error) {
            alert(error.message);
        } finally {
            analyzeBtn.disabled = false;
            analyzeBtn.textContent = '분석하기';
            loadingIndicator.style.display = 'none';
        }
    });
}); 