import json
import unittest
from unittest.mock import patch
import server

class Contracts(unittest.TestCase):
    def test_valid_request(self):
        server.validate_request('/generate', dict(level='beginner', category='business', expression='meeting'))
    def test_invalid_level(self):
        with self.assertRaises(ValueError): server.validate_request('/generate', dict(level='expert',category='business',expression='meeting'))
    def test_no_user_id_transmitted(self):
        with self.assertRaises(ValueError): server.validate_request('/feedback', dict(answer='Hello',language='ko',userId='secret'))
    def test_invalid_json_fields(self):
        for value in [{}, {'english':'Hello','korean':'안녕','extra':True}, {'english':5,'korean':'안녕'}, []]:
            with self.assertRaises(ValueError): server.validate_output('/generate', value)
    def test_wrong_language(self):
        with self.assertRaises(ValueError): server.validate_output('/generate',dict(english='I have a meeting.',korean='Meeting today'))
    def test_level_constraint(self):
        with self.assertRaises(ValueError): server.validate_output('/generate',dict(english='Hi.',korean='안녕.'),dict(level='beginner'))
    def test_valid_output(self):
        server.validate_output('/generate',dict(english='I have a meeting today.',korean='오늘 회의가 있어요.'),dict(level='beginner'))
    def test_uncertainty_type(self):
        with self.assertRaises(ValueError): server.validate_output('/feedback',dict(corrected='Hello.',explanation='안녕.',uncertain='false'))
    def test_turn_bounds(self):
        for turn in [-1,5,True]:
            with self.assertRaises(ValueError): server.validate_request('/question',dict(level='beginner',turn=turn))
    @patch('server.urllib.request.urlopen',side_effect=OSError('offline'))
    def test_offline_retries_and_fails_honestly(self, request):
        with self.assertRaises(RuntimeError): server.infer('/question',dict(level='beginner',turn=0))
        self.assertEqual(2, request.call_count)
    @patch('server.urllib.request.urlopen')
    def test_malformed_model_json_rejected(self,request):
        request.return_value.__enter__.return_value.read.return_value=json.dumps({'message':{'content':'not JSON'}}).encode()
        with self.assertRaises(RuntimeError): server.infer('/question',dict(level='beginner',turn=0))


class RegenerationContracts(unittest.TestCase):
    def test_previous_examples_are_optional_and_bounded(self):
        server.validate_request('/generate', dict(level='beginner',category='business',expression='meeting',previous_examples=['I have a meeting today.']))
        for previous in ['invalid', ['x']*7, [123], ['x'*301]]:
            with self.assertRaises(ValueError): server.validate_request('/generate',dict(level='beginner',category='business',expression='meeting',previous_examples=previous))
    def test_duplicate_ignores_punctuation_and_case(self):
        request=dict(level='beginner',previous_examples=['We have a meeting today.'])
        with self.assertRaisesRegex(ValueError,'repeated'):
            server.validate_output('/generate',dict(english='WE have a meeting today!',korean='오늘 회의가 있어요.'),request)
    @patch('server.urllib.request.urlopen')
    def test_duplicate_retries_for_a_new_sentence(self,request):
        def output(en): return json.dumps({'message':{'content':json.dumps(dict(english=en,korean='회의를 준비합니다.'))}}).encode()
        request.return_value.__enter__.return_value.read.side_effect=[output('We have a meeting today.'),output('Please prepare the meeting notes.')]
        result=server.infer('/generate',dict(level='beginner',category='business',expression='meeting',previous_examples=['We have a meeting today.']))
        self.assertEqual('Please prepare the meeting notes.',result['english'])
        self.assertEqual(2,request.call_count)

if __name__ == '__main__': unittest.main()
