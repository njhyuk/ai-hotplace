document.addEventListener('DOMContentLoaded', () => {
    const analyzeBtn = document.getElementById('analyzeBtn');
    const reviewUrl = document.getElementById('reviewUrl');
    const resultContainer = document.querySelector('.result-container');
    const summaryText = document.getElementById('summaryText');
    const positivesList = document.getElementById('positivesList');
    const negativesList = document.getElementById('negativesList');

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
            analyzeBtn.disabled = true;
            analyzeBtn.textContent = '분석 중...';

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

            const result = await response.json();
            
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
        }
    });
}); 